package com.apstudio.sentieri.db

interface OnLayerClickListener {
    fun onswcVisibileClick(position: Int, isChecked: Boolean)
    fun onswcDirezioneClick(position: Int, isChecked: Boolean)
    fun onswcQuotaPendenzaClick(position: Int, isChecked: Boolean)
    fun onbtnSeguiClick(position: Int)
    fun onItemLongClick(position: Int)
}
