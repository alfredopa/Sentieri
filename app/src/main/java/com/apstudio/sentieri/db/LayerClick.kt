package com.apstudio.sentieri.db

interface OnLayerClickListener {
    fun onswcVisibileClick(position: Int)
    fun onswcDirezioneClick(position: Int)
    fun onbtnSeguiClick(position: Int)
    fun onItemLongClick(position: Int)
}