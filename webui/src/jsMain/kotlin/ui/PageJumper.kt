package top.moles.ui

import emotion.react.css
import react.FC
import react.Props
import react.PropsWithValue
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ul
import top.moles.Pagination
import web.cssom.*
import kotlin.math.max
import kotlin.math.min

external interface PropsWithOnClick : Props {
    var onClick: ()->Unit
}
external interface PropsWithOnChange<CallBackData> : Props {
    var onChange: (CallBackData) -> Unit
}

data class OnChangeMessage (
    val oldValue: UInt,
    val newValue: UInt,
)
external interface PageJumperProps : PropsWithValue<Pagination>, PropsWithOnChange<OnChangeMessage>

data class ClickLiMessage<T> (
    val clickAble: Boolean,
    val content: String,
    val callbackData: T
)

external interface ClickLiProps: PropsWithValue<Pair<Boolean, String>>, PropsWithOnClick

val ClickLi = FC<ClickLiProps> { props ->
    val (clickAble, content) = props.value
    li {
        css {
            margin = Margin(0.em, 0.5.em)
            transition = Transition(PropertyName.backgroundColor, (0.3).s, delay = 0.s)
            listStyleType = None.none
            if (clickAble) {
                color = Color("#158fc5")
                cursor = Cursor.pointer
                hover {
                    backgroundColor = NamedColor.silver
                }
            }
        }

        + content
        if(clickAble) {
            onClick = {props.onClick()}
        }
    }
}

val PageJumper = FC<PageJumperProps> { props ->
    val current = props.value.current
    val maxPage = props.value.total / props.value.pageSize
    val displaySize = min(5u, maxPage)
    val pageList = mutableListOf(current)
    while(pageList.size.toUInt() < displaySize) {
        val left = pageList.first()
        val right = pageList.last()
        if (left > 0u) {
            pageList.add(0, left - 1u)
        }
        if (right < maxPage) {
            pageList.add(right + 1u)
        }
    }
    ul {
        css {
            display = Display.flex
            listStyleType = None.none
            margin = Margin(1.em, 0.pct, 0.pct)
            padding = Padding(0.em, 0.em)
            width = 100.pct
            justifyContent = JustifyContent.center
        }

        ClickLi {
            value = (current != 0u) to "上一页"
            onClick = {
                props.onChange(OnChangeMessage(
                    oldValue = current,
                    newValue = current - 1u,
                ))
            }
        }
        pageList.forEach { page ->
            ClickLi {
                value = (current != page) to "${page+1u}"
                onClick = {
                    props.onChange(OnChangeMessage(
                        oldValue = current,
                        newValue = page,
                    ))
                }
            }
        }
        ClickLi {
            value = (current != maxPage) to "下一页"
            onClick = {
                props.onChange(OnChangeMessage(
                    oldValue = current,
                    newValue = current + 1u,
                ))
            }
        }
    }
}