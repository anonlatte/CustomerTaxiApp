package com.example.taxiapp.data.model

data class User(
        var id: Int = -1,
        var phoneNumber: String = "",
        var savedToken: String = "",
        var lastLogin: Long = 0,
        var loginTime: Long = 0,
        var order: Order = Order()
)