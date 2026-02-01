package ru.newlevel.autocanverter.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.newlevel.autocanverter.BLEViewModel


val presentationModule = module {
    viewModel {
        BLEViewModel()
    }
}