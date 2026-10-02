package com.flowkeyboard.android

import android.app.Application
import com.flowkeyboard.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FlowKeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@FlowKeyboardApp); modules(appModule) }
    }
}
