package com.iliverez.spoldify.ui.common.widgets

import android.widget.ImageView
import com.iliverez.spoldify.R
import com.iliverez.spoldify.util.ImageLoader

object BindingAdapters {

    @JvmStatic
    fun loadArt(view: ImageView, uri: String?) {
        ImageLoader.loadArt(view.context, uri, view, R.drawable.ic_music_placeholder)
    }

    @JvmStatic
    fun loadArtThumbnail(view: ImageView, uri: String?) {
        ImageLoader.loadArtThumbnail(view.context, uri, view, R.drawable.ic_music_placeholder)
    }
}
