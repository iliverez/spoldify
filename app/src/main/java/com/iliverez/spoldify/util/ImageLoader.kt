package com.iliverez.spoldify.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

object ImageLoader {

    fun loadArt(context: Context, uri: String?, target: ImageView, placeholder: Int) {
        if (uri.isNullOrBlank()) {
            target.setImageResource(placeholder)
            return
        }
        Glide.with(context)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(200)
            .centerCrop()
            .placeholder(placeholder)
            .into(target)
    }

    fun loadArtThumbnail(context: Context, uri: String?, target: ImageView, placeholder: Int) {
        if (uri.isNullOrBlank()) {
            target.setImageResource(placeholder)
            return
        }
        Glide.with(context)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(80)
            .centerCrop()
            .placeholder(placeholder)
            .into(target)
    }

    fun preload(context: Context, uri: String?) {
        if (uri.isNullOrBlank()) return
        Glide.with(context)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(200)
            .preload()
    }
}
