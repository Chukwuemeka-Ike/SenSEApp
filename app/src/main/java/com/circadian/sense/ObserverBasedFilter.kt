package com.circadian.sense

import org.ejml.data.DMatrixRMaj


class ObserverBasedFilter {

    var A: DMatrixRMaj
    var B: DMatrixRMaj
    var C: DMatrixRMaj
    var D: DMatrixRMaj
    var y: DMatrixRMaj


    init {
        A = DMatrixRMaj(2, 2)
        B = DMatrixRMaj(1, 1)
        C = DMatrixRMaj(1, 1)
        D = DMatrixRMaj(1, 1)
        y = loadData()
    }

    private fun loadData(): DMatrixRMaj {
//        var data = Array<Int> (2)
        return DMatrixRMaj(2, 30)
    }

    private fun simulateDynamics(order: Int, t: DMatrixRMaj, y: DMatrixRMaj): DMatrixRMaj {
        val stateLength = (order*2) + 1
        var xHat = DMatrixRMaj(stateLength,t.numRows)

//        for (i in 0 until t.numRows){
//            for (j in 0 until stateLength) {
//                xHat[j, i] =
//            }
//        }

        return xHat
    }


}