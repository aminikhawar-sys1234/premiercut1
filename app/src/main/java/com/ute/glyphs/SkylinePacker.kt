package com.ute.glyphs

/** Skyline bottom-left bin packer — the standard atlas packing algorithm. */
class SkylinePacker(private val width: Int, private val height: Int) {

    data class Node(var x: Int, var y: Int, var width: Int)

    private val skyline = ArrayList<Node>().apply { add(Node(0, 0, width)) }
    var usedHeight = 0; private set

    data class Placement(val x: Int, val y: Int)

    fun insert(w: Int, h: Int): Placement? {
        if (w > width || h > height) return null
        var bestIdx = -1; var bestY = Int.MAX_VALUE; var bestX = -1

        var i = 0
        while (i < skyline.size) {
            val y = fitAt(i, w, h)
            if (y != null) {
                if (y < bestY || (y == bestY && skyline[i].x < bestX)) {
                    bestY = y; bestIdx = i; bestX = skyline[i].x
                }
            }
            i++
        }
        if (bestIdx < 0) return null

        val node = Node(bestX, bestY + h, w)
        skyline.add(bestIdx, node)
        var k = bestIdx + 1
        while (k < skyline.size) {
            val n = skyline[k]
            if (n.x < node.x + node.width) {
                val shrink = node.x + node.width - n.x
                if (n.width <= shrink) { skyline.removeAt(k); continue } else { n.x += shrink; n.width -= shrink }
            }
            k++
        }
        merge(); usedHeight = maxOf(usedHeight, bestY + h)
        return Placement(bestX, bestY)
    }

    private fun fitAt(index: Int, w: Int, h: Int): Int? {
        val x = skyline[index].x
        if (x + w > width) return null
        var remaining = w; var y = skyline[index].y
        var i = index
        while (remaining > 0) {
            if (i >= skyline.size) return null
            y = maxOf(y, skyline[i].y)
            if (y + h > height) return null
            remaining -= skyline[i].width
            i++
        }
        return y
    }

    private fun merge() {
        var i = 0
        while (i < skyline.size - 1) {
            if (skyline[i].y == skyline[i + 1].y) {
                skyline[i].width += skyline[i + 1].width
                skyline.removeAt(i + 1)
            } else i++
        }
    }
}
