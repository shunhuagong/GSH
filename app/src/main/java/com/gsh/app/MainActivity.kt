package com.gsh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gsh.app.ui.GshApp
import com.gsh.app.ui.GshTheme
import com.gsh.app.ui.GshViewModel

class MainActivity : ComponentActivity() {

    private val vm: GshViewModel by viewModels(factoryProducer = { GshViewModel.Factory })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dynamicColor by vm.dynamicColor.collectAsState()
            GshTheme(dynamicColor = dynamicColor) {
                GshApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到应用时若已跨天，自动定位到新的一天
        vm.refreshIfNewDay()
    }
}
