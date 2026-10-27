package top.moles.ui

import react.FC
import react.PropsWithClassName


@OptIn(ExperimentalJsExport::class)
@JsExport
data class SelectOption(
    val value: String,
    val label: String,
    val key: String? = null,
)
external interface SelectProps : PropsWithClassName {
    var options: Array<SelectOption>
    var showSearch: Boolean
    var onChange: (value: String) -> Unit
    var optionFilterProp: String
}

@JsModule("antd")
@JsNonModule
external object Antd {
    val Select: FC<SelectProps>
}
