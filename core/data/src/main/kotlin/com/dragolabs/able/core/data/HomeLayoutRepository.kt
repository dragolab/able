package com.dragolabs.able.core.data

import com.dragolabs.able.core.database.dao.HomeCardDao
import com.dragolabs.able.core.database.entity.HomeCardConfigEntity
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.HomeCardType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistent Home card order and visibility (HOME-002, ADR-016). Missing card types (for example
 * after an update adds a new card) are appended using their default visibility.
 */
class HomeLayoutRepository(private val dao: HomeCardDao) {

    val cards: Flow<List<HomeCardConfig>> = dao.getAllCards().map { stored ->
        normalize(stored.mapNotNull { runCatching { it.toDomain() }.getOrNull() })
    }

    suspend fun setVisible(type: HomeCardType, visible: Boolean) = update { list ->
        list.map { if (it.cardType == type) it.copy(isVisible = visible) else it }
    }

    /** Moves [type] by [offset] positions (-1 up, +1 down). */
    suspend fun move(type: HomeCardType, offset: Int) = update { list ->
        val from = list.indexOfFirst { it.cardType == type }
        if (from < 0) return@update list
        val to = (from + offset).coerceIn(0, list.lastIndex)
        list.toMutableList().apply { add(to, removeAt(from)) }
    }

    suspend fun resetToDefault() {
        dao.clearAll()
        dao.insertOrUpdateCards(HomeCardConfig.defaultCards().map(HomeCardConfigEntity::fromDomain))
    }

    private suspend fun update(transform: (List<HomeCardConfig>) -> List<HomeCardConfig>) {
        val current = cards.first()
        val updated = transform(current).mapIndexed { index, card -> card.copy(displayOrder = index) }
        dao.insertOrUpdateCards(updated.map(HomeCardConfigEntity::fromDomain))
    }

    private fun normalize(stored: List<HomeCardConfig>): List<HomeCardConfig> {
        if (stored.isEmpty()) return HomeCardConfig.defaultCards()
        val known = stored.sortedBy { it.displayOrder }
        val missing = HomeCardConfig.defaultCards().filter { d -> known.none { it.cardType == d.cardType } }
        return (known + missing).mapIndexed { index, card -> card.copy(displayOrder = index) }
    }
}
