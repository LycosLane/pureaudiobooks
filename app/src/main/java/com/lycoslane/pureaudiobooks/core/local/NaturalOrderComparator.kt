package com.lycoslane.pureaudiobooks.core.local

/**
 * Compares strings naturally so "Track 2.mp3" < "Track 10.mp3" and "CD1/01" < "CD2/01".
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        while (ia < a.length && ib < b.length) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = ia
                val startB = ib
                while (ia < a.length && a[ia].isDigit()) ia++
                while (ib < b.length && b[ib].isDigit()) ib++
                val numA = a.substring(startA, ia).trimStart('0')
                val numB = b.substring(startB, ib).trimStart('0')
                if (numA.length != numB.length) {
                    return numA.length.compareTo(numB.length)
                }
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
                // Same numeric value, e.g. "01" vs "1": shorter zero-prefix first
                val lenCmp = (ia - startA).compareTo(ib - startB)
                if (lenCmp != 0) return lenCmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                ia++
                ib++
            }
        }
        return a.length.compareTo(b.length)
    }
}
