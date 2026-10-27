package top.moles.ui.ark

import emotion.css.css
import emotion.react.css
import react.FC
import react.Props
import react.PropsWithClassName
import react.PropsWithValue
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import top.moles.ui.PropsWithOnClick
import web.cssom.*

external interface ButtonProps : PropsWithOnClick, PropsWithValue<String>

val Button = FC <ButtonProps> { props ->
    div {
        label {
            onClick = {
                props.onClick()
            }
            css {
                display = Display.block
                whiteSpace = WhiteSpace.nowrap
                height = 1.em
                padding = Padding((0.5).em, (1.5).em)
                boxSizing = BoxSizing.contentBox
                minHeight = 1.em
                minWidth = 1.em
                borderRadius = 1.em
                lineHeight = 1.em
                backgroundColor = Color("#158fc5")
                backgroundRepeat = BackgroundRepeat.noRepeat
                backgroundSize = 100.pct
                cursor = Cursor.pointer
                userSelect = None.none
                transition = Transition(PropertyName.background, (0.3).s, delay = 0.s)
                textAlign = TextAlign.center

                hover {
                    backgroundColor = Color("#1174a0")
                }
            }
            span {
                css { color = NamedColor.white }
                + props.value
            }
        }
    }
}