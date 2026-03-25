package ovh.gabrielhuav.sensores_escom_v2.presentation.locations.buildings.gobierno

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import ovh.gabrielhuav.sensores_escom_v2.R
import ovh.gabrielhuav.sensores_escom_v2.data.map.Bluetooth.BluetoothGameManager
import ovh.gabrielhuav.sensores_escom_v2.data.map.OnlineServer.OnlineServerManager
import ovh.gabrielhuav.sensores_escom_v2.domain.bluetooth.BluetoothManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.common.managers.MovementManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.common.managers.ServerConnectionManager
import ovh.gabrielhuav.sensores_escom_v2.presentation.components.BuildingNumber2
import ovh.gabrielhuav.sensores_escom_v2.presentation.game.mapview.MapMatrixProvider
import ovh.gabrielhuav.sensores_escom_v2.presentation.game.mapview.MapView
import ovh.gabrielhuav.sensores_escom_v2.presentation.common.base.GameplayActivity
import ovh.gabrielhuav.sensores_escom_v2.presentation.game.penales.PenalesActivity

class Canchasgestion : AppCompatActivity(),
    BluetoothManager.BluetoothManagerCallback,
    BluetoothGameManager.ConnectionListener,
    OnlineServerManager.WebSocketListener,
    MapView.MapTransitionListener {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var movementManager: MovementManager
    private lateinit var serverConnectionManager: ServerConnectionManager
    private lateinit var mapView: MapView

    private lateinit var btnNorth: Button
    private lateinit var btnSouth: Button
    private lateinit var btnEast: Button
    private lateinit var btnWest: Button
    private lateinit var btnBackToHome: Button
    private lateinit var tvBluetoothStatus: TextView
    private lateinit var btnB2: Button
    private lateinit var btnA: Button
    private lateinit var playerName: String
    private var gameState = BuildingNumber2.GameState()

    private var canChangeMap = false
    private var targetDestination: String? = null
    private var isAtExitPoint = false
    private var pendingMapDestination: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canchasgestion)

        try {
            // Inicializar el mapView con tu imagen jpeg
            mapView = MapView(
                context = this,
                mapResourceId = R.drawable.escom_canchas_gestion
            )
            findViewById<FrameLayout>(R.id.map_container).addView(mapView)

            initializeComponents(savedInstanceState)

            mapView.post {
                // Configurar el mapa con la constante que creamos
                mapView.setCurrentMap(MapMatrixProvider.Companion.MAP_CANCHAS_GESTION, R.drawable.escom_canchas_gestion)
                mapView.playerManager.setCurrentMap(MapMatrixProvider.Companion.MAP_CANCHAS_GESTION)
                mapView.updateLocalPlayerPosition(gameState.playerPosition)


                if (gameState.isConnected) {
                    serverConnectionManager.sendUpdateMessage(playerName, gameState.playerPosition, MapMatrixProvider.Companion.MAP_CANCHAS_GESTION)
                }
            }
        } catch (e: Exception) {
            Log.e("CanchasGestion", "Error inicializando: ${e.message}")
            finish()
        }
    }

    private fun initializeComponents(savedInstanceState: Bundle?) {
        playerName = intent.getStringExtra("PLAYER_NAME") ?: "Jugador"

        if (savedInstanceState == null) {
            gameState.isConnected = intent.getBooleanExtra("IS_CONNECTED", false)
            gameState.playerPosition = intent.getSerializableExtra("INITIAL_POSITION") as? Pair<Int, Int> ?: Pair(38, 20)
        }

        initializeViews()
        initializeManagers()
        movementManager.setPosition(gameState.playerPosition)
        setupButtonListeners()

        if (gameState.isConnected) connectToOnlineServer()
    }

    private fun initializeViews() {
        btnNorth = findViewById(R.id.button_north)
        btnSouth = findViewById(R.id.button_south)
        btnEast = findViewById(R.id.button_east)
        btnWest = findViewById(R.id.button_west)
        btnBackToHome = findViewById(R.id.button_back_to_home)
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus)
        btnB2 = findViewById(R.id.button_small_2)
        tvBluetoothStatus.text = "Canchas Gestión - Conectando..."
    }

    private fun initializeManagers() {
        bluetoothManager = BluetoothManager.Companion.getInstance(this, tvBluetoothStatus).apply {
            setCallback(this@Canchasgestion)
        }
        val onlineServerManager = OnlineServerManager.Companion.getInstance(this).apply {
            setListener(this@Canchasgestion)
        }
        serverConnectionManager = ServerConnectionManager(this, onlineServerManager)
        mapView.playerManager.localPlayerId = playerName
        movementManager = MovementManager(mapView) { position -> updatePlayerPosition(position) }
        mapView.setMapTransitionListener(this)
    }

    private fun setupButtonListeners() {
        btnNorth.setOnTouchListener { _, event -> movementManager.handleMovement(event, 0, -1); true }
        btnSouth.setOnTouchListener { _, event -> movementManager.handleMovement(event, 0, 1); true }
        btnEast.setOnTouchListener { _, event -> movementManager.handleMovement(event, 1, 0); true }
        btnWest.setOnTouchListener { _, event -> movementManager.handleMovement(event, -1, 0); true }
        btnA = findViewById(R.id.button_a)
        btnBackToHome.setOnClickListener { finish() }
        btnB2.setOnClickListener { finish() }

        btnA.setOnClickListener {
            if (canChangeMap) {
                when (targetDestination) {"minijuego_penales" -> {
                    Log.d("Penales", "Abriendo minijuego...")
                    val intent = Intent(this, PenalesActivity::class.java)
                    startActivity(intent)
                }
                    MapMatrixProvider.Companion.MAP_MAIN -> {
                        onMapTransitionRequested(MapMatrixProvider.Companion.MAP_MAIN, Pair(8, 35))
                    }
                }
            }
        }
    }

    private fun updatePlayerPosition(position: Pair<Int, Int>) {
        val x = position.first
        val y = position.second

        runOnUiThread {
            gameState.playerPosition = position
            mapView.updateLocalPlayerPosition(position)
            mapView.forceRecenterOnPlayer()

            // 1. Detección del Minijuego (Rango 18-20 en X, 17-19 en Y)
            if ((x in 18..20) && (y in 17..19)) {
                canChangeMap = true
                targetDestination = "minijuego_penales"
                Toast.makeText(this, "Presiona A para Jugar Penales", Toast.LENGTH_SHORT).show()
            }
            // 2. Detección de Salida (Rango 34-36 en X, 19-21 en Y)
            else if ((x in 34..36) && (y in 19..21)) {
                canChangeMap = true
                targetDestination = MapMatrixProvider.Companion.MAP_MAIN
                Toast.makeText(this, "Presiona A para Salir", Toast.LENGTH_SHORT).show()
            }
            // 3. Fuera de puntos interactivos
            else {
                canChangeMap = false
                targetDestination = null
            }

            if (gameState.isConnected) {
                serverConnectionManager.sendUpdateMessage(playerName, position, MapMatrixProvider.Companion.MAP_CANCHAS_GESTION)
            }
        }
    }

    private fun connectToOnlineServer() {
        serverConnectionManager.connectToServer { success ->
            runOnUiThread {
                gameState.isConnected = success
                if (success) {
                    serverConnectionManager.onlineServerManager.sendJoinMessage(playerName)
                    tvBluetoothStatus.text = "Canchas Gestión - En Línea"
                }
            }
        }
    }

    override fun onMessageReceived(message: String) {
        // Aquí puedes copiar la lógica de update de PalapasIA si quieres ver a otros jugadores
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        movementManager.setPosition(gameState.playerPosition)

        if (gameState.isConnected) {
            connectToOnlineServer()
        }
    }

    // Métodos requeridos por las interfaces (pueden quedar vacíos por ahora)
    override fun onBluetoothDeviceConnected(device: BluetoothDevice) {}
    override fun onBluetoothConnectionFailed(error: String) {}
    override fun onConnectionComplete() {}
    override fun onConnectionFailed(message: String) {}
    override fun onDeviceConnected(device: BluetoothDevice) {}
    override fun onPositionReceived(device: BluetoothDevice, x: Int, y: Int) {}
    override fun onMapTransitionRequested(targetMap: String, initialPosition: Pair<Int, Int>) {
        if (targetMap == MapMatrixProvider.Companion.MAP_MAIN) {
            val intent = Intent(this, GameplayActivity::class.java).apply {
                putExtra("PLAYER_NAME", playerName)
                putExtra("IS_CONNECTED", gameState.isConnected)
                // Ponemos Y=35 para que aparezca arriba del punto azul de entrada (que está en 36)
                putExtra("INITIAL_POSITION", Pair(8, 35))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
    }
}