package com.iliverez.spoldify.zeroconf

import androidx.lifecycle.MutableLiveData

class ZeroconfStateLiveData : MutableLiveData<ZeroconfManager.ZeroconfState>() {
    init {
        value = ZeroconfManager.ZeroconfState.IDLE
    }
}
