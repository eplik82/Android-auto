package com.eplik82.parkedvideo

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/**
 * Teksti sisestamine auto klaviatuuriga. Auto pinnale joonistatud brauseril
 * oma klaviatuuri pole – Android Auto annab klaviatuuri ainult
 * SearchTemplate'is (ja ainult seisvas autos).
 */
class TextInputScreen(
    carContext: CarContext,
    private val onText: (String) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                screenManager.pop()
                if (searchText.isNotBlank()) onText(searchText)
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_text_hint))
            .setShowKeyboardByDefault(true)
            .setItemList(
                ItemList.Builder().setNoItemsMessage(carContext.getString(R.string.car_text_info)).build()
            )
            .build()
}
