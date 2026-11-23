package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.HomeCardType

@Entity(tableName = "home_card_configs")
data class HomeCardConfigEntity(
    @PrimaryKey
    val cardType: String,
    val isVisible: Boolean,
    val displayOrder: Int
) {
    fun toDomain(): HomeCardConfig = HomeCardConfig(
        cardType = HomeCardType.valueOf(cardType),
        isVisible = isVisible,
        displayOrder = displayOrder
    )

    companion object {
        fun fromDomain(domain: HomeCardConfig): HomeCardConfigEntity = HomeCardConfigEntity(
            cardType = domain.cardType.name,
            isVisible = domain.isVisible,
            displayOrder = domain.displayOrder
        )
    }
}
