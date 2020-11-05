package tech.ijkzen.scancodeview

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import tech.ijkzen.scancodeview.databinding.ItemResultBinding
import java.util.zip.Inflater

class ResultAdapter : RecyclerView.Adapter<ResultAdapter.ViewHolder>() {

    private val mList = ArrayList<String>()

    fun setList(list: List<String>?) {
        mList.clear()
        list?.let {
            mList.addAll(list)
        }

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindViewHolder(mList[position])
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    inner class ViewHolder(private val itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mBinding = ItemResultBinding.bind(itemView)


        fun bindViewHolder(result: String) {
            mBinding.text.text = result
        }
    }
}