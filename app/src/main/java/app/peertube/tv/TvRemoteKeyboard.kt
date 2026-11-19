package app.peertube.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

internal enum class TvKeyboardLayout { TEXT, URL, NUMBER, PASSWORD }

@Composable
internal fun TvRemoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardLayout: TvKeyboardLayout = TvKeyboardLayout.TEXT,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    labelStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
) {
    var keyboardOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val useBuiltInKeyboard = remember(context) {
        context.getSharedPreferences("peertube", 0).getBoolean("useBuiltInTvKeyboard", true)
    }
    OutlinedTextField(
        value = value,
        onValueChange = if (useBuiltInKeyboard) ({}) else onValueChange,
        enabled = enabled,
        readOnly = useBuiltInKeyboard,
        singleLine = singleLine,
        label = { Text(label, style = labelStyle) },
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = when (keyboardLayout) {
            TvKeyboardLayout.URL -> KeyboardType.Uri
            TvKeyboardLayout.NUMBER -> KeyboardType.NumberPassword
            TvKeyboardLayout.PASSWORD -> KeyboardType.Password
            TvKeyboardLayout.TEXT -> KeyboardType.Text
        }),
        modifier = modifier.onPreviewKeyEvent { event ->
            if (useBuiltInKeyboard && enabled && event.type == KeyEventType.KeyDown && event.key in setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) {
                keyboardOpen = true
                true
            } else onPreviewKeyEvent(event)
        }.then(if (useBuiltInKeyboard) Modifier.clickable(enabled = enabled) { keyboardOpen = true } else Modifier)
    )
    if (useBuiltInKeyboard && keyboardOpen) {
        TvRemoteKeyboardDialog(
            label = label,
            value = value,
            layout = keyboardLayout,
            onValueChange = onValueChange,
            onDismiss = { keyboardOpen = false }
        )
    }
}

@Composable
private fun TvRemoteKeyboardDialog(
    label: String,
    value: String,
    layout: TvKeyboardLayout,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var symbols by remember(layout) { mutableStateOf(layout == TvKeyboardLayout.PASSWORD) }
    var uppercase by remember { mutableStateOf(false) }
    var russian by remember { mutableStateOf(false) }
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKeyFocus.requestFocus() } }

    val rows = when {
        layout == TvKeyboardLayout.NUMBER -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("0"))
        symbols -> listOf(
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
            listOf("-", "_", "=", "+", "[", "]", "{", "}", "\\", "|"),
            listOf(":", ";", "'", "\"", ",", ".", "/", "?", "<", ">")
        )
        layout == TvKeyboardLayout.URL -> listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            "qwertyuiop".map(Char::toString),
            "asdfghjkl".map(Char::toString),
            listOf("z", "x", "c", "v", "b", "n", "m", ".", "-", "_")
        )
        russian -> listOf("1234567890".map(Char::toString), "йцукенгшщзх".map(Char::toString), "фывапролджэ".map(Char::toString), "ячсмитьбюё".map(Char::toString))
        else -> listOf("1234567890".map(Char::toString), "qwertyuiop".map(Char::toString), "asdfghjkl".map(Char::toString), "zxcvbnm".map(Char::toString))
    }

    fun append(text: String) = onValueChange(value + text)
    fun deleteLast() = onValueChange(value.dropLast(1))
    fun pressKey(key: String) {
        when (key) {
            "BACKSPACE" -> deleteLast()
            "SPACE" -> append(" ")
            "CLEAR" -> onValueChange("")
            "URL" -> append("https://")
            else -> append(if (uppercase && key.length == 1 && key[0].isLetter()) key.uppercase() else key)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).widthIn(max = 900.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().focusGroup().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = if (layout == TvKeyboardLayout.PASSWORD) "•".repeat(value.length) else value,
                    onValueChange = {}, readOnly = true, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_keyboard_input)) }
                )
                rows.forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { columnIndex, char ->
                            val shown = if (uppercase && char.length == 1 && char[0].isLetter()) char.uppercase() else char
                            OutlinedButton(
                                onClick = { pressKey(char) },
                                modifier = Modifier.weight(1f).height(42.dp).then(if (rowIndex == 0 && columnIndex == 0) Modifier.focusRequester(firstKeyFocus) else Modifier),
                                contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp)
                            ) { Text(shown, maxLines = 1) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (layout == TvKeyboardLayout.URL) KeyboardAction(stringResource(R.string.account_keyboard_url), Modifier.weight(1.5f)) { pressKey("URL") }
                    if (layout != TvKeyboardLayout.NUMBER) {
                        KeyboardAction("@", Modifier.weight(1f)) { append("@") }
                        KeyboardAction("/", Modifier.weight(1f)) { append("/") }
                        KeyboardAction(":", Modifier.weight(1f)) { append(":") }
                        KeyboardAction(".", Modifier.weight(1f)) { append(".") }
                        if (layout == TvKeyboardLayout.TEXT) KeyboardAction(stringResource(R.string.account_keyboard_space), Modifier.weight(1.2f)) { pressKey("SPACE") }
                    }
                    KeyboardAction("⌫", Modifier.weight(1.2f), enabled = value.isNotEmpty()) { deleteLast() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyboardAction(stringResource(R.string.account_keyboard_clear), Modifier.weight(1f)) { onValueChange("") }
                    if (layout != TvKeyboardLayout.NUMBER) {
                        KeyboardAction(stringResource(if (symbols) R.string.account_keyboard_letters else R.string.account_keyboard_symbols), Modifier.weight(1f)) {
                            symbols = !symbols; uppercase = false
                        }
                        if (layout == TvKeyboardLayout.TEXT) KeyboardAction(if (russian) "EN" else "РУ", Modifier.weight(.75f)) { russian = !russian }
                        if (!symbols) KeyboardAction("⇧", Modifier.weight(.75f)) { uppercase = !uppercase }
                    }
                    KeyboardAction(stringResource(R.string.account_keyboard_done), Modifier.weight(1f)) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun KeyboardAction(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(42.dp), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
        Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
