package com.piggytrade.piggytrade

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.piggytrade.piggytrade.stablecoin.StablecoinRegistry

class PiggyApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Register all stablecoin protocols once at app startup.
        // To add a new protocol: implement StablecoinProtocol and call register() here.
        StablecoinRegistry.initialize()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
