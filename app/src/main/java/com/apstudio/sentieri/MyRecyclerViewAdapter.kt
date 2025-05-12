package com.apstudio.sentieri

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.databinding.FragmentDettaglioSentieriBinding
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.prnDiscesa
import com.apstudio.sentieri.db.prnDislivello
import java.text.DecimalFormat

class MyRecyclerViewAdapter(private val clickListener: (Sentieri) -> Unit) :
    RecyclerView.Adapter<MyViewHolder>() {
    private var sentieriList = emptyList<Sentieri>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding: FragmentDettaglioSentieriBinding =
            DataBindingUtil.inflate(layoutInflater, R.layout.fragment_dettaglio_sentieri, parent, false)
        return MyViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return sentieriList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: List<Sentieri>){
        sentieriList = newData
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) =
        holder.bind(sentieriList[position], clickListener)

    /*fun setList(sentieri: List<Sentieri>) {
        sentieriList. clear()
        sentieriList.addAll(sentieri)
    }*/
}

class MyViewHolder(private val binding: FragmentDettaglioSentieriBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(sentieri: Sentieri, clickListener: (Sentieri) -> Unit) {
        binding.nomeText.text = sentieri.nome
        binding.lunghezzaText.text = MapUtils.formattastring(sentieri.lunghezza.toInt())
        //binding.lunghezzaText.text = sentieri.prnLunghezza()
        binding.dislivelloText.text = sentieri.prnDislivello()
        binding.discesaText.text  = sentieri.prnDiscesa()
        binding.hrmediaText.text  = sentieri.HrMed.toString()
        binding.hrmaxText.text   = sentieri.HrMax.toString()
        binding.DataText.text = MapUtils.prnDataFromUtc(sentieri.DataOra)
        binding.fineText.text = MapUtils.prnDataFromUtc(sentieri.DataFine)
        binding.velMediaText.text = DecimalFormat("##.##").format(sentieri.MediaVel)
        binding.tDataInizioText.text = MapUtils.formatSeconds(sentieri.TempoTot.toLong())
        binding.tDataFineText.text = MapUtils.formatSeconds(sentieri.TempoInMov.toLong())
        //binding.tMediaText.text = DecimalFormat("##.#").format(sentieri.TempMedia)
        //binding.tMaxText.text = DecimalFormat("##.#").format(sentieri.TempMax)
        //binding.tMinText.text = DecimalFormat("##.#").format(sentieri.TempMin)
        binding.rigaDettaglio.setOnClickListener {
            clickListener(sentieri)
        }
    }
}