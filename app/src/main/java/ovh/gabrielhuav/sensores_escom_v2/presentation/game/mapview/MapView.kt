package ovh.gabrielhuav.sensores_escom_v2.presentation.game.mapview

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import ovh.gabrielhuav.sensores_escom_v2.R
import ovh.gabrielhuav.sensores_escom_v2.data.map.OnlineServer.OnlineServerManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.game.zombie.FogOfWarRenderer
import kotlin.math.min

class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val mapResourceId: Int = R.drawable.escom_mapa
) : View(context, attrs), OnlineServerManager.WebSocketListener {

    companion object {
        fun createWithCustomMap(context: Context, mapResourceId: Int): MapView {
            return MapView(context, mapResourceId = mapResourceId)
        }
    }

    private val renderer = MapRenderer()
    private val gestureHandler = MapGestureHandler(this)
    val playerManager = PlayerManager()
    val mapState = MapState()
    private val handler = Handler(Looper.getMainLooper())

    private var currentMapId = MapMatrixProvider.MAP_MAIN
    private var mapMatrix = MapMatrix(currentMapId)

    private var isUserInteracting = false
    private var shouldCenterOnPlayer = true
    private var transitionListener: MapTransitionListener? = null

    // Fog of War properties
    private var fogOfWarRenderer: FogOfWarRenderer? = null
    private var fogOfWarEnabled = false
    private var visionRadius: Int = 10

    interface MapTransitionListener {
        fun onMapTransitionRequested(targetMap: String, initialPosition: Pair<Int, Int>)
    }

    interface CustomDrawCallback {
        fun onCustomDraw(canvas: Canvas, cellWidth: Float, cellHeight: Float)
    }

    interface CarRenderer {
        fun drawCars(canvas: Canvas)
    }

    private var customDrawCallback: CustomDrawCallback? = null
    private var carRenderer: CarRenderer? = null

    fun setCustomDrawCallback(callback: CustomDrawCallback?) {
        customDrawCallback = callback
    }

    fun setCarRenderer(renderer: CarRenderer?) {
        this.carRenderer = renderer
    }

    fun setFogOfWarRenderer(renderer: FogOfWarRenderer) {
        this.fogOfWarRenderer = renderer
    }

    fun setFogOfWarEnabled(enabled: Boolean) {
        this.fogOfWarEnabled = enabled
        invalidate()
    }

    fun setVisionRadius(radius: Int) {
        this.visionRadius = radius
        invalidate()
    }

    init {
        isClickable = true
        isFocusable = true
        setupGestureHandler()
        post {
            try {
                loadMapBitmap()
                postDelayed({
                    adjustMapToScreen()
                    invalidate()
                }, 100)
            } catch (e: Exception) {
                Log.e("MapView", "Error en init: ${e.message}")
            }
        }
    }

    fun setMapTransitionListener(listener: MapTransitionListener) {
        transitionListener = listener
    }

    fun setCurrentMap(mapId: String, resourceId: Int) {
        try {
            if (currentMapId != mapId) {
                currentMapId = mapId
                mapMatrix = MapMatrix(mapId)
                post {
                    try {
                        loadMapBitmap(resourceId)
                        postDelayed({
                            adjustMapToScreen()
                            playerManager.setCurrentMap(mapId)
                            playerManager.getLocalPlayerPosition()?.let {
                                centerMapOnPlayer()
                            }
                            invalidate()
                            Log.d("MapView", "Mapa cambiado a: $mapId")
                        }, 100)
                    } catch (e: Exception) {
                        Log.e("MapView", "Error en setCurrentMap: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MapView", "Error cambiando mapa: ${e.message}")
        }
    }

    private fun loadMapBitmap(resourceId: Int = mapResourceId) {
        try {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inMutable = true
            }
            val bitmap = BitmapFactory.decodeResource(resources, resourceId, options)
            if (bitmap != null) {
                mapState.backgroundBitmap = bitmap
                gestureHandler.setBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("MapView", "Error cargando mapa: ${e.message}")
        }
    }

    private fun setupGestureHandler() {
        gestureHandler.initializeDetectors(context)
        gestureHandler.setCallback(object : MapGestureHandler.GestureCallback {
            override fun onOffsetChanged(offsetX: Float, offsetY: Float) {
                isUserInteracting = true
                shouldCenterOnPlayer = false
                mapState.offsetX += offsetX
                mapState.offsetY += offsetY
            }
            override fun onScaleChanged(scaleFactor: Float) {
                isUserInteracting = true
                shouldCenterOnPlayer = false
                mapState.scaleFactor = scaleFactor
            }
            override fun invalidateView() { invalidate() }
            override fun constrainOffset() { constrainMapOffset() }
        })
    }

    private fun centerMapOnPlayer() {
        try {
            playerManager.getLocalPlayerPosition()?.let { (playerX, playerY) ->
                mapState.backgroundBitmap?.let { bitmap ->
                    val scaledWidth = bitmap.width * mapState.scaleFactor
                    val scaledHeight = bitmap.height * mapState.scaleFactor
                    val cellWidth = scaledWidth / MapMatrixProvider.MAP_WIDTH.toFloat()
                    val cellHeight = scaledHeight / MapMatrixProvider.MAP_HEIGHT.toFloat()
                    val playerPosX = playerX * cellWidth + (cellWidth / 2)
                    val playerPosY = playerY * cellHeight + (cellHeight / 2)
                    
                    mapState.offsetX = (width / 2f) - playerPosX
                    mapState.offsetY = (height / 2f) - playerPosY
                    constrainMapOffset()
                }
            }
        } catch (e: Exception) {
            Log.e("MapView", "Error en centerMapOnPlayer: ${e.message}")
        }
    }

    fun forceRecenterOnPlayer() {
        centerMapOnPlayer()
        invalidate()
    }

    private fun constrainMapOffset() {
        mapState.backgroundBitmap?.let { bitmap ->
            val scaledWidth = bitmap.width * mapState.scaleFactor
            val scaledHeight = bitmap.height * mapState.scaleFactor
            if (scaledWidth <= width) mapState.offsetX = (width - scaledWidth) / 2f
            else mapState.offsetX = mapState.offsetX.coerceIn(width - scaledWidth, 0f)
            if (scaledHeight <= height) mapState.offsetY = (height - scaledHeight) / 2f
            else mapState.offsetY = mapState.offsetY.coerceIn(height - scaledHeight, 0f)
        }
    }

    fun adjustMapToScreen() {
        if (width <= 0 || height <= 0) return
        mapState.backgroundBitmap?.let { bitmap ->
            val isLandscapeMap = currentMapId == MapMatrixProvider.MAP_CANCHA_IA || 
                                currentMapId == "canchas_gestion"
            
            if (isLandscapeMap) {
                // Forzar que el mapa de la cancha llene la pantalla a lo ancho
                val scale = width.toFloat() / bitmap.width
                mapState.scaleFactor = scale
                mapState.offsetX = 0f
                mapState.offsetY = (height - (bitmap.height * scale)) / 2f
            } else {
                val scaleX = width.toFloat() / bitmap.width
                val scaleY = height.toFloat() / bitmap.height
                val finalScale = Math.min(scaleX, scaleY) * 0.98f
                mapState.scaleFactor = finalScale
                mapState.offsetX = (width - (bitmap.width * finalScale)) / 2f
                mapState.offsetY = (height - (bitmap.height * finalScale)) / 2f
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.postDelayed({
            adjustMapToScreen()
            centerMapOnPlayer()
            invalidate()
        }, 300)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { isUserInteracting = true; shouldCenterOnPlayer = false }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isUserInteracting = false; constrainMapOffset() }
        }
        parent.requestDisallowInterceptTouchEvent(true)
        val handled = gestureHandler.onTouchEvent(event)
        constrainMapOffset()
        invalidate()
        return handled
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mapState.setMapMatrix(mapMatrix)
        renderer.draw(canvas, mapState, playerManager)
        
        val bitmapWidth = mapState.backgroundBitmap?.width?.toFloat() ?: return
        val bitmapHeight = mapState.backgroundBitmap?.height?.toFloat() ?: return
        val cellWidth = bitmapWidth / MapMatrixProvider.MAP_WIDTH
        val cellHeight = bitmapHeight / MapMatrixProvider.MAP_HEIGHT

        customDrawCallback?.let { callback ->
            canvas.save()
            canvas.translate(mapState.offsetX, mapState.offsetY)
            canvas.scale(mapState.scaleFactor, mapState.scaleFactor)
            callback.onCustomDraw(canvas, cellWidth, cellHeight)
            canvas.restore()
        }

        carRenderer?.let { renderer ->
            canvas.save()
            canvas.translate(mapState.offsetX, mapState.offsetY)
            canvas.scale(mapState.scaleFactor, mapState.scaleFactor)
            renderer.drawCars(canvas)
            canvas.restore()
        }

        if (fogOfWarEnabled) {
            fogOfWarRenderer?.let { fr ->
                val playerPos = playerManager.getLocalPlayerPosition() ?: Pair(0, 0)
                canvas.save()
                canvas.translate(mapState.offsetX, mapState.offsetY)
                canvas.scale(mapState.scaleFactor, mapState.scaleFactor)
                fr.drawFogOfWar(canvas, playerPos, cellWidth, cellHeight, visionRadius)
                canvas.restore()
            }
        }
    }

    fun updateLocalPlayerPosition(position: Pair<Int, Int>?, forceCenter: Boolean = false) {
        val prevPosition = playerManager.getLocalPlayerPosition()
        playerManager.updateLocalPlayerPosition(position)
        if (position != null && (forceCenter || (!isUserInteracting && (prevPosition == null || prevPosition != position)))) {
            centerMapOnPlayer()
        }
        invalidate()
    }

    fun updateRemotePlayerPosition(playerId: String, position: Pair<Int, Int>, map: String) {
        playerManager.updateRemotePlayerPosition(playerId, position, map)
        invalidate()
    }

    fun removeRemotePlayer(playerId: String) {
        playerManager.removeRemotePlayer(playerId)
        invalidate()
    }

    override fun onMessageReceived(message: String) {
        playerManager.handleWebSocketMessage(message)
        invalidate()
    }

    fun setBluetoothServerMode(isServer: Boolean) {}

    fun getPlayerRect(position: Pair<Int, Int>): RectF? {
        val bitmap = mapState.backgroundBitmap ?: return null
        val cellWidth = bitmap.width / MapMatrixProvider.MAP_WIDTH.toFloat()
        val cellHeight = bitmap.height / MapMatrixProvider.MAP_HEIGHT.toFloat()
        val playerX = position.first * cellWidth
        val playerY = position.second * cellHeight
        val playerSize = min(cellWidth, cellHeight) * 0.8f
        return RectF(
            playerX + (cellWidth - playerSize) / 2f,
            playerY + (cellHeight - playerSize) / 2f,
            playerX + (cellWidth + playerSize) / 2f,
            playerY + (cellHeight + playerSize) / 2f
        )
    }

    fun isValidPosition(x: Int, y: Int): Boolean = mapMatrix.isValidPosition(x, y)
    fun isInteractivePosition(x: Int, y: Int): Boolean = mapMatrix.isInteractivePosition(x, y)
    fun getMapTransitionPoint(x: Int, y: Int): String? = mapMatrix.isMapTransitionPoint(x, y)
    
    fun initiateMapTransition(targetMap: String) {
        val currentPos = playerManager.getLocalPlayerPosition() ?: Pair(0, 0)
        transitionListener?.onMapTransitionRequested(targetMap, currentPos)
    }

    fun updateSpecialEntity(entityId: String, position: Pair<Int, Int>, map: String) {
        playerManager.updateSpecialEntity(entityId, position, map)
        invalidate()
    }

    fun removeSpecialEntity(entityId: String) {
        playerManager.removeSpecialEntity(entityId)
        invalidate()
    }

    fun zoomToFitGame() {
        mapState.scaleFactor = 2.5f
        constrainMapOffset()
        invalidate()
    }
}
