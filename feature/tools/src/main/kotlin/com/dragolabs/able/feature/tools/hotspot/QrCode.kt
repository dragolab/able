package com.dragolabs.able.feature.tools.hotspot

/**
 * Minimal QR Code (ISO/IEC 18004) encoder: byte mode, error correction M (L as fallback),
 * versions 1-40, automatic mask selection. Written for the hotspot join code so Able needs no QR
 * library. The structure follows the public specification and the well-known reference layout
 * used by Project Nayuki's QR Code generator (MIT).
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {

    fun isDark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    enum class Ecc(val formatBits: Int, internal val tableIndex: Int) { L(1, 0), M(0, 1) }

    companion object {
        /** Returns null when [text] does not fit in a version-40 symbol. */
        fun encode(text: String): QrCode? {
            val data = text.encodeToByteArray()
            for (ecc in listOf(Ecc.M, Ecc.L)) {
                for (version in 1..40) {
                    val capacityBits = numDataCodewords(version, ecc) * 8
                    val usedBits = 4 + charCountBits(version) + data.size * 8
                    if (usedBits <= capacityBits) return build(version, ecc, data)
                }
            }
            return null
        }

        internal fun charCountBits(version: Int) = if (version <= 9) 8 else 16

        private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
            // L
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            // M
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
        )
        private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
            // L
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            // M
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
        )

        internal fun numRawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val numAlign = version / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        internal fun numDataCodewords(version: Int, ecc: Ecc): Int =
            numRawDataModules(version) / 8 -
                ECC_CODEWORDS_PER_BLOCK[ecc.tableIndex][version] * NUM_ERROR_CORRECTION_BLOCKS[ecc.tableIndex][version]

        private fun build(version: Int, ecc: Ecc, data: ByteArray): QrCode {
            val capacity = numDataCodewords(version, ecc)
            val bits = BitBuffer()
            bits.append(0b0100, 4) // byte mode
            bits.append(data.size, charCountBits(version))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            bits.append(0, minOf(4, capacity * 8 - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity * 8) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            val codewords = ByteArray(capacity) { i ->
                var b = 0
                for (j in 0 until 8) b = (b shl 1) or (if (bits[i * 8 + j]) 1 else 0)
                b.toByte()
            }
            val all = addEccAndInterleave(codewords, version, ecc)
            return Matrix(version).apply {
                drawFunctionPatterns(ecc)
                drawCodewords(all)
            }.finish(ecc)
        }

        internal fun addEccAndInterleave(data: ByteArray, version: Int, ecc: Ecc): ByteArray {
            val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecc.tableIndex][version]
            val blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecc.tableIndex][version]
            val rawCodewords = numRawDataModules(version) / 8
            val numShortBlocks = numBlocks - rawCodewords % numBlocks
            val shortBlockLen = rawCodewords / numBlocks
            val divisor = reedSolomonDivisor(blockEccLen)
            val blocks = ArrayList<ByteArray>(numBlocks)
            var k = 0
            for (i in 0 until numBlocks) {
                val datLen = shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1
                val dat = data.copyOfRange(k, k + datLen)
                k += datLen
                val block = dat.copyOf(shortBlockLen + 1)
                val eccBytes = reedSolomonRemainder(dat, divisor)
                eccBytes.copyInto(block, block.size - blockEccLen)
                blocks += block
            }
            val result = ByteArray(rawCodewords)
            var r = 0
            for (i in blocks[0].indices) {
                for (j in blocks.indices) {
                    if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) result[r++] = blocks[j][i]
                }
            }
            return result
        }

        internal fun reedSolomonDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = gfMultiply(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        internal fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                result.copyInto(result, 0, 1, result.size)
                result[result.size - 1] = 0
                for (i in result.indices) {
                    result[i] = (result[i].toInt() xor gfMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
                }
            }
            return result
        }

        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        /** 15-bit format information for [ecc] and [mask], including the BCH code and XOR mask. */
        internal fun formatBits(ecc: Ecc, mask: Int): Int {
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            return ((data shl 10) or rem) xor 0x5412
        }

        internal fun versionBits(version: Int): Int {
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            return (version shl 12) or rem
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size get() = bits.size
        operator fun get(i: Int) = bits[i]
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += ((value ushr i) and 1) != 0
        }
    }

    private class Matrix(val version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        fun drawFunctionPatterns(ecc: Ecc) {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.size - 1
            for (i in positions.indices) {
                for (j in positions.indices) {
                    if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
                    drawAlignment(positions[i], positions[j])
                }
            }
            drawFormat(ecc, 0)
            drawVersion()
        }

        private fun drawFinder(x: Int, y: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until size && yy in 0 until size) set(xx, yy, dist != 2 && dist != 4)
                }
            }
        }

        private fun drawAlignment(x: Int, y: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) set(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        fun drawFormat(ecc: Ecc, mask: Int) {
            val bits = formatBits(ecc, mask)
            fun bit(i: Int) = ((bits ushr i) and 1) != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            val bits = versionBits(version)
            for (i in 0 until 18) {
                val dark = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y][x] && i < data.size * 8) {
                            modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (isFunction[y][x]) continue
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert) modules[y][x] = !modules[y][x]
                }
            }
        }

        fun finish(ecc: Ecc): QrCode {
            var bestMask = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask)
                drawFormat(ecc, mask)
                val penalty = penalty()
                if (penalty < bestPenalty) {
                    bestPenalty = penalty
                    bestMask = mask
                }
                applyMask(mask) // XOR again to undo
            }
            applyMask(bestMask)
            drawFormat(ecc, bestMask)
            return QrCode(size, modules)
        }

        private fun penalty(): Int {
            var result = 0
            fun line(get: (Int) -> Boolean) {
                var runColor = get(0)
                var runLength = 1
                for (i in 1 until size) {
                    val c = get(i)
                    if (c == runColor) {
                        runLength++
                        if (runLength == 5) result += 3 else if (runLength > 5) result += 1
                    } else {
                        runColor = c
                        runLength = 1
                    }
                }
                val pattern1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
                val pattern2 = pattern1.reversedArray()
                for (start in 0..size - pattern1.size) {
                    if (pattern1.indices.all { get(start + it) == pattern1[it] }) result += 40
                    if (pattern2.indices.all { get(start + it) == pattern2[it] }) result += 40
                }
            }
            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = modules[y][x]
                    if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3
                }
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += maxOf(0, k) * 10
            return result
        }
    }
}
