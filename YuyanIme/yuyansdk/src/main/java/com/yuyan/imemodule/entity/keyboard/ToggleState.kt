package com.yuyan.imemodule.entity.keyboard

class ToggleState {
    var stateId = 0
    var label: String = ""

    constructor(stateId: Int) {
        this.stateId = stateId
    }

    constructor(keyLabel: String, stateId: Int) {
        this.stateId = stateId
        label = keyLabel
    }
}
