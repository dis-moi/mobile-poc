package com.dismoi.scout.floating

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.dismoi.scout.floating.layout.Bubble
import com.dismoi.scout.floating.layout.Layout
import com.dismoi.scout.floating.layout.Message
import com.dismoi.scout.floating.FloatingService.FloatingServiceBinder

class Manager private constructor(private val context: Context) {
  private var bounded = false
  private var floatingService: FloatingService? = null
  private var trashLayoutResourceId = 0
  private var listener: OnCallback? = null
  private val disMoiServiceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      val binder = service as FloatingServiceBinder
      floatingService = binder.service
      configureBubblesService()
      bounded = true
      listener?.onInitialized()
    }

    override fun onServiceDisconnected(name: ComponentName) {
      bounded = false
    }
  }

  private fun configureBubblesService() {
    floatingService!!.addTrash(trashLayoutResourceId)
  }

  fun initialize() {
    context.bindService(
      Intent(context, FloatingService::class.java),
      disMoiServiceConnection,
      Context.BIND_AUTO_CREATE
    )
  }

  fun recycle() {
    context.unbindService(disMoiServiceConnection)
  }

  fun addDisMoiBubble(bubble: Layout?, x: Int, y: Int) {
    if (bounded) {
      floatingService!!.addDisMoiBubble(bubble as Bubble, x, y)
    }
  }

  fun addDisMoiMessage(message: Layout?, y: Int) {
    if (bounded) {
      floatingService!!.addDisMoiMessage(message as Message, y)
    }
  }

  fun removeDisMoiBubble(bubble: Layout?) {
    if (bounded) {
      floatingService!!.removeBubble(bubble as Bubble?)
    }
  }

  fun removeDisMoiMessage(message: Layout?) {
    if (bounded) {
      floatingService!!.removeMessage(message as Message)
    }
  }

  class Builder(context: Context) {
    private val disMoiManager: Manager
    fun setInitializationCallback(listener: OnCallback): Builder {
      disMoiManager.listener = listener
      return this
    }

    fun setTrashLayout(trashLayoutResourceId: Int): Builder {
      disMoiManager.trashLayoutResourceId = trashLayoutResourceId
      return this
    }

    fun build(): Manager {
      return disMoiManager
    }

    init {
      disMoiManager = getInstance(context)
    }
  }

  companion object {
    private fun getInstance(context: Context): Manager {
      return Manager(context)
    }
  }
}