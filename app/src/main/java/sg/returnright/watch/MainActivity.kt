package sg.returnright.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.returnright.watch.navigation.*
import sg.returnright.watch.ui.ReturnRightApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: StationViewModel = viewModel()
            val feed by viewModel.feed.collectAsStateWithLifecycle()
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val sensors = remember { WatchSensors(this@MainActivity) }
            var permissionRevision by remember { mutableIntStateOf(0) }
            var position by remember { mutableStateOf(LocationState()) }
            var heading by remember { mutableStateOf(Heading()) }
            var compassActive by remember { mutableStateOf(false) }
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissionRevision++
            }
            val hasLocation = remember(permissionRevision) {
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            LaunchedEffect(permissionRevision) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    sensors.location().collect {
                        position = it
                        viewModel.updateSearchPosition(it.position)
                    }
                }
            }
            LaunchedEffect(compassActive) {
                if (compassActive) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    heading = Heading(message = "Starting compass")
                    sensors.heading().collect { heading = it }
                }
            }
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        while (true) { now = System.currentTimeMillis(); delay(1_000) }
                    }
                    launch {
                        while (true) {
                            // Preserve an explicitly selected demo until the user requests live data.
                            if (!viewModel.feed.value.demo) viewModel.refresh()
                            delay(viewModel.nextAutoRefreshDelay())
                        }
                    }
                }
            }
            ReturnRightApp(feed, position, heading, hasLocation, now,
                onRefresh = { viewModel.refresh(manual = true) },
                onDemo = viewModel::showDemo,
                onLocation = { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) },
                onCompassActive = { compassActive = it })
        }
    }
}
