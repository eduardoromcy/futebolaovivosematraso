package com.e2dsys.futebolaovivosematraso

import android.app.Application
import com.e2dsys.futebolaovivosematraso.youtube.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader())
    }
}
