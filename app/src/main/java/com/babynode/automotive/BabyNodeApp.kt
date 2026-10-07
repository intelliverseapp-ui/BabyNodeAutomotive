package com.babynode.automotive

import android.app.Application

class BabyNodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BabyNodeApp
            private set
    }
}
