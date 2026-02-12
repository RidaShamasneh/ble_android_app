package com.example.first_application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.first_application.ui.theme.First_applicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_applicationTheme {
                CalculatorScreen()
            }
        }
    }
}

@Composable
fun CalculatorScreen() {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var operator by remember { mutableStateOf<String?>(null) }

    fun onDigit(digit: String) {
        display = if (display == "0") digit else display + digit
    }

    fun onOperator(op: String) {
        operand1 = display.toDoubleOrNull()
        operator = op
        display = "0"
    }

    fun onEquals() {
        val op1 = operand1
        val op2 = display.toDoubleOrNull()
        if (op1 != null && op2 != null && operator != null) {
            val result = when (operator) {
                "+" -> op1 + op2
                "-" -> op1 - op2
                "*" -> op1 * op2
                "/" -> if (op2 != 0.0) op1 / op2 else Double.NaN
                else -> op2
            }
            display = result.toString()
            operand1 = null
            operator = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = display, style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))

        val buttons = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", "=", "+")
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { label ->
                    Button(
                        onClick = {
                            when (label) {
                                in "0".."9" -> onDigit(label)
                                "+", "-", "*", "/" -> onOperator(label)
                                "=" -> onEquals()
                            }
                        },
                        modifier = Modifier.weight(1f).padding(4.dp)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}
