package com.yuyan.imemodule.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.yuyan.imemodule.R
import com.yuyan.imemodule.adapter.CandidatesAdapter
import com.yuyan.imemodule.adapter.PrefixAdapter
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.data.theme.ThemeManager.activeTheme
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.database.entry.SideSymbol
import com.yuyan.imemodule.entity.keyboard.SoftKey
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import com.yuyan.imemodule.service.DecodingInfo
import com.yuyan.imemodule.singleton.EnvironmentSingleton.Companion.instance
import com.yuyan.imemodule.utils.AppUtil
import com.yuyan.imemodule.utils.DevicesUtils
import com.yuyan.imemodule.utils.DevicesUtils.dip2px
import com.yuyan.imemodule.utils.thread.ThreadPoolUtils
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.manager.layout.CustomFlexboxLayoutManager
import com.yuyan.imemodule.libs.recyclerview.SwipeRecyclerView
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.margin
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

@SuppressLint("ViewConstructor")
class CandidatesContainer(context: Context, inputView: InputView) : BaseContainer(context, inputView) {
    private val mSideSymbolsPinyin:List<SideSymbol>
    private lateinit var mRVSymbolsView: SwipeRecyclerView
    private lateinit var mCandidatesAdapter: CandidatesAdapter
    private var mRVLeftPrefix = inflate(getContext(), R.layout.sdk_view_rv_prefix, null) as SwipeRecyclerView
    private var isLoadingMore = false
    private val mLlAddSymbol : LinearLayout = LinearLayout(context).apply{
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { margin = (dp(20)) }
        gravity = Gravity.CENTER
    }
    init {
        initView(context)
        val ivAddSymbol = ImageView(context).apply {
            setPadding(dp(5))
            setImageResource(R.drawable.ic_menu_setting)
        }
        ivAddSymbol.setOnClickListener { _:View ->
            val arguments = Bundle()
            arguments.putInt("type", 0)
            AppUtil.launchSettingsToPrefix(context, arguments)
        }
        mLlAddSymbol.addView(ivAddSymbol)
        mSideSymbolsPinyin = DataBaseKT.instance.sideSymbolDao().getAllSideSymbolPinyin()
    }

    private fun initView(context: Context) {
        mRVSymbolsView = SwipeRecyclerView(context)
        mRVSymbolsView.setHasFixedSize(true)
        mRVSymbolsView.setItemAnimator(null)
        mRVLeftPrefix.setLayoutManager(LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false))
        val skbHeightMargins = (instance.skbHeight * 0.01).toInt()
        add(mRVLeftPrefix, lParams(width = (instance.skbWidth * 0.18).toInt(), height = matchParent).apply {
            setMargins(0, skbHeightMargins, 0, skbHeightMargins)
            leftOfParent(0)
        })
        mRVLeftPrefix.visibility = GONE
        add(mRVSymbolsView, lParams(width = 0, height = matchParent).apply {
            startToEnd = mRVLeftPrefix.id
            endOfParent(0)
        })
        addView(getIvDelete())
        val manager = CustomFlexboxLayoutManager(context)
        manager.flexDirection = FlexDirection.ROW
        manager.flexWrap = FlexWrap.WRAP
        manager.justifyContent = JustifyContent.SPACE_AROUND
        mRVSymbolsView.setLayoutManager(manager)
        mRVSymbolsView.addOnScrollListener(object :RecyclerView.OnScrollListener(){
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    ThreadPoolUtils.executeSingleton {
                        if (!isLoadingMore) {
                            isLoadingMore = true
                            val lastItem = (recyclerView.layoutManager as CustomFlexboxLayoutManager).findLastCompletelyVisibleItemPosition()
                            DecodingInfo.activeCandidate = lastItem
                            if (DecodingInfo.candidateSize - lastItem <= 5) {
                                DecodingInfo.nextPageCandidates
                            }
                            isLoadingMore = false
                        }
                    }
                }
            }
        })
        mRVSymbolsView.addFooterView(View(context).apply {
            layoutParams = FlexboxLayoutManager.LayoutParams(FlexboxLayoutManager.LayoutParams.MATCH_PARENT, dp(50))
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun getIvDelete(): ImageView {
        val ivDelete = ImageView(context)
        ivDelete.setImageResource(R.drawable.sdk_skb_key_delete_icon)
        val paddingBorder = dip2px(10f)
        ivDelete.setPadding(paddingBorder, paddingBorder, paddingBorder, paddingBorder)
        val isKeyBorder = ThemeManager.prefs.keyBorder.getValue()
        if (isKeyBorder) {
            val mActiveTheme = activeTheme
            val keyRadius = ThemeManager.prefs.keyRadius.getValue()
            val bg = GradientDrawable()
            bg.setColor(mActiveTheme.keyBackgroundColor)
            bg.shape = GradientDrawable.RECTANGLE
            bg.cornerRadius = keyRadius.toFloat()
            ivDelete.background = bg
        }
        ivDelete.isClickable = true
        ivDelete.setEnabled(true)
        ivDelete.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    DevicesUtils.tryPlayKeyDown(KeyEvent.KEYCODE_DEL)
                    DevicesUtils.tryVibrate(this)
                }
                MotionEvent.ACTION_MOVE -> { }
                MotionEvent.ACTION_UP -> {
                    inputView.responseKeyEvent(SoftKey(KeyEvent.KEYCODE_DEL))
                    if(DecodingInfo.isFinish) {
                        KeyboardManager.instance.switchKeyboard()
                        (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
                    }
                }
            }
            true
        }
        ivDelete.layoutParams = lParams(width =  wrapContent, height = wrapContent).apply {
            bottomOfParent(paddingBorder)
            rightOfParent(paddingBorder)
        }
        return ivDelete
    }

    
    fun showCandidatesView() {
        if (DecodingInfo.isCandidatesListEmpty || DecodingInfo.isAssociate){
            mRVSymbolsView.removeAllViews()
        } else {
            if(DecodingInfo.activeCandidate == 0){
                mCandidatesAdapter = CandidatesAdapter(context)
                mCandidatesAdapter.setOnItemClickLitener { _: RecyclerView.Adapter<*>?, _: View?, position: Int ->
                    DevicesUtils.tryPlayKeyDown()
                    DevicesUtils.tryVibrate(this)
                    inputView.chooseAndUpdate(position)
                }
                mRVSymbolsView.setAdapter(mCandidatesAdapter)
            } else {
                mCandidatesAdapter.notifyDataSetChanged()
            }
            if (InputModeSwitcherManager.isChineseT9) {
                mRVLeftPrefix.visibility = VISIBLE
                updatePrefixsView()
            }
        }
    }

    private fun updatePrefixsView() {
        var prefixs =DecodingInfo.prefixs
        val isPrefixs = prefixs.isNotEmpty()
        if (!isPrefixs) {
            prefixs = mSideSymbolsPinyin.map { it.symbolKey }.toTypedArray()
            if (mRVLeftPrefix.footerCount <= 0) mRVLeftPrefix.addFooterView(mLlAddSymbol)
        } else{
            if (mRVLeftPrefix.footerCount > 0) mRVLeftPrefix.removeFooterView(mLlAddSymbol)
        }
        mRVLeftPrefix.setAdapter(null)
        mRVLeftPrefix.setOnItemClickListener{ _: View?, position: Int ->
            if (isPrefixs) {
                inputView.selectPrefix(position)
            } else {
                val softKey = SoftKey(label = mSideSymbolsPinyin.map { it.symbolValue }[position])
                DevicesUtils.tryPlayKeyDown()
                DevicesUtils.tryVibrate(this)
                inputView.responseKeyEvent(softKey)
            }
        }
        mRVLeftPrefix.setAdapter(PrefixAdapter(context, prefixs))
    }
}