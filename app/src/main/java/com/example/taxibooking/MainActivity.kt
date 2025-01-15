package com.example.taxibooking

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.Circle

data class Driver(val name: String, val position: LatLng)

class MainActivity : ComponentActivity() {

    private val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentLocation by mutableStateOf(LatLng(0.0, 0.0))

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            var isPermissionGranted by remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsResult ->
                val allGranted = permissionsResult.all { it.value }
                if (allGranted) {
                    isPermissionGranted = true
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(Unit) {
                if (permissions.all {
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            it
                        ) == PackageManager.PERMISSION_GRANTED
                    }) {
                    isPermissionGranted = true
                    startLocationUpdates()
                } else {
                    launcher.launch(permissions)
                }
            }

            val drivers = listOf(
                Driver("Driver A", LatLng(9.6615, 80.0255)),
                Driver("Driver B", LatLng(9.71, 80.08)),
                Driver("Driver C", LatLng(9.7938, 80.2210))
            )

            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "screen_A") {
                composable("screen_A") {
                    ScreenA(
                        navController = navController,
                        currentLocation = currentLocation,
                        isPermissionGranted = isPermissionGranted,
                        drivers = drivers,
                        calculateDistance = ::calculateDistance
                    )
                }
                composable("screen_B?driverName={driverName}") { backStackEntry ->
                    val driverName = backStackEntry.arguments?.getString("driverName") ?: "Unknown"
                    ScreenB(driverName)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun calculateDistance(latLng1: LatLng, latLng2: LatLng): Double {
        val location1 = Location("Location1").apply {
            latitude = latLng1.latitude
            longitude = latLng1.longitude
        }
        val location2 = Location("Location2").apply {
            latitude = latLng2.latitude
            longitude = latLng2.longitude
        }
        return location1.distanceTo(location2).toDouble() / 1000
    }
}

@Composable
fun ScreenA(
    navController: NavController,
    currentLocation: LatLng,
    isPermissionGranted: Boolean,
    drivers: List<Driver>,
    calculateDistance: (LatLng, LatLng) -> Double
) {
    var nearbyDriver by remember { mutableStateOf<Driver?>(null) }

    LaunchedEffect(currentLocation) {
        nearbyDriver = drivers.minByOrNull { calculateDistance(currentLocation, it.position) }
            ?.takeIf { calculateDistance(currentLocation, it.position) < 1.0 }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f)
                .padding(8.dp)
                .border(2.dp, Color.Black)
        ) {
            LocationMap(currentLocation, isPermissionGranted, drivers)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                drivers.forEach { driver ->
                    val distance = calculateDistance(currentLocation, driver.position)
                    Text("Distance to ${driver.name}    :      ${"%.2f".format(distance)} km")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.1f)
                .padding(8.dp)
        ) {
            Button(
                onClick = { navController.navigate("screen_B?driverName=${nearbyDriver?.name ?: ""}") },
                enabled = nearbyDriver != null,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("Book Now")
            }
        }
    }
}

@Composable
private fun LocationMap(
    currentLocation: LatLng,
    isPermissionGranted: Boolean,
    drivers: List<Driver>
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    LaunchedEffect(currentLocation) {
        try {
            cameraPositionState.move(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(currentLocation, 15f)))
        } catch (e: Exception) {
            Log.e("LocationMap", "Error updating camera position: ${e.message}")
        }
    }

    if (isPermissionGranted) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .height(300.dp),
            cameraPositionState = cameraPositionState
        ) {
            Circle(
                center = currentLocation,
                clickable = true,
                fillColor = Color.Blue,
                radius = 100.0,
                strokeColor = Color.Red,
                strokeWidth = 2.0f,
                zIndex = 1.0f,
                onClick = {
                    Log.d("LocationMap", "User's location circle clicked.")
                }
            )

            drivers.forEach { driver ->
                Marker(
                    state = rememberMarkerState(position = driver.position),
                    title = driver.name,
                    snippet = "Driver Location"
                )
            }
        }
    } else {
        Toast.makeText(LocalContext.current, "Location permission required", Toast.LENGTH_SHORT)
            .show()
    }
}

    @Composable
    fun ScreenB(driverName: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Booking ride with $driverName",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
            )

            var name by remember { mutableStateOf("") }
            var mobileNo by remember { mutableStateOf("") }

            var tripYear by remember { mutableStateOf("") }
            var tripMonth by remember { mutableStateOf("") }
            var tripDay by remember { mutableStateOf("") }
            var tripHour by remember { mutableStateOf("") }
            var tripMinute by remember { mutableStateOf("") }
            var tripAM_PM by remember { mutableStateOf("") }

            var destination by remember { mutableStateOf("") }

            var bookYear by remember { mutableStateOf("") }
            var bookMonth by remember { mutableStateOf("") }
            var bookDay by remember { mutableStateOf("") }
            var bookHour by remember { mutableStateOf("") }
            var bookMinute by remember { mutableStateOf("") }
            var bookAM_PM by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth())
                if (name.isBlank()) {
                    Text(
                        "Name is required",
                        color = Color.Red,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mobileNo,
                    onValueChange = { mobileNo = it },
                    label = { Text("Mobile No") },
                    isError = mobileNo.length != 10 || !mobileNo.all { it.isDigit() },
                    modifier = Modifier.fillMaxWidth()
                )
                if (mobileNo.length != 10 || !mobileNo.all { it.isDigit() }) {
                    Text(
                        "Enter a valid 10-digit mobile number",
                        color = Color.Red,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.padding(2.dp)) {

                    Column {
                    Text("Trip Date & Time")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = tripYear,
                            onValueChange = { tripYear = it },
                            label = { Text("Year") },
                            placeholder = { Text("YYYY") },
                            isError = !tripYear.matches(Regex("\\d{4}")),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = tripMonth,
                            onValueChange = { tripMonth = it },
                            label = { Text("Month") },
                            placeholder = { Text("MM") },
                            isError = !tripMonth.matches(Regex("\\d{2}")),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = tripDay,
                            onValueChange = { tripDay = it },
                            label = { Text("Day") },
                            placeholder = { Text("DD") },
                            isError = !tripDay.matches(Regex("\\d{2}")),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = tripHour,
                            onValueChange = { tripHour = it },
                            label = { Text("Hour") },
                            placeholder = { Text("HH") },
                            isError = !tripHour.matches(Regex("\\d{2}")),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = tripMinute,
                            onValueChange = { tripMinute = it },
                            label = { Text("Minute") },
                            placeholder = { Text("MM") },
                            isError = !tripMinute.matches(Regex("\\d{2}")),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tripAM_PM,
                            onValueChange = { tripAM_PM = it },
                            label = { Text("AM/PM") },
                            placeholder = { Text("AM/PM") },
                            isError = tripAM_PM !in listOf("AM", "PM"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                    isError = destination.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (destination.isBlank()) {
                    Text(
                        "Destination is required",
                        color = Color.Red,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.padding(2.dp)) {

                    Column {
                        Text("Booking Date & Time")

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = bookYear,
                                onValueChange = { bookYear = it },
                                label = { Text("Year") },
                                placeholder = { Text("YYYY") },
                                isError = !bookYear.matches(Regex("\\d{4}")),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = bookMonth,
                                onValueChange = { bookMonth = it },
                                label = { Text("Month") },
                                placeholder = { Text("MM") },
                                isError = !bookMonth.matches(Regex("\\d{2}")),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = bookDay,
                                onValueChange = { bookDay = it },
                                label = { Text("Day") },
                                placeholder = { Text("DD") },
                                isError = !bookDay.matches(Regex("\\d{2}")),
                                modifier = Modifier.weight(1f)
                            )
                        }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = bookHour,
                        onValueChange = { bookHour = it },
                        label = { Text("Hour") },
                        placeholder = { Text("HH") },
                        isError = !bookHour.matches(Regex("\\d{2}")),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = bookMinute,
                        onValueChange = { bookMinute = it },
                        label = { Text("Minute") },
                        placeholder = { Text("MM") },
                        isError = !bookMinute.matches(Regex("\\d{2}")),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bookAM_PM,
                        onValueChange = { bookAM_PM = it },
                        label = { Text("AM/PM") },
                        placeholder = { Text("AM/PM") },
                        isError = bookAM_PM !in listOf("AM", "PM"),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
                Spacer(modifier = Modifier.height(8.dp))

                var checked by remember { mutableStateOf(false) }

                Text(
                    text = "Payment Method")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cash Payment",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                        }
                    )

                    Text(
                        text = "Credit Card",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(2.dp)
                        .border(2.dp, Color.Black)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Name:")
                            Text(text = name)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Mobile No:")
                            Text(text = mobileNo)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Trip Date & Time:")
                            Text(text = "${tripYear} / ${tripMonth} / ${tripDay}  @  ${tripHour} : ${tripMinute} ${tripAM_PM}")
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Destination:")
                            Text(text = destination)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Trip Date & Time:")
                            Text(text = "${bookYear} / ${bookMonth} / ${bookDay}  @  ${bookHour} : ${bookMinute} ${bookAM_PM}")
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Driver Name:")
                            Text(text = driverName)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Payment Method:")
                            Text(
                                text = if (checked) "Credit Card" else "Cash Payment"
                            )
                        }
                    }
                }
            }
        }
    }