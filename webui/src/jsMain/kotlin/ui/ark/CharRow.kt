package top.moles.ui.ark

import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.li
import top.moles.Rarity
import web.cssom.*

external interface CharRowProps : Props {
    var name: String
    var rarity: Rarity
    var isNew: Boolean
}

val CharRow = FC<CharRowProps> { props ->
    val isNew = props.isNew
    li {
        +props.name
        css {
            listStyleType = None.none
            position = Position.relative
            display = Display.listItem

            color = when (props.rarity) {
                3 -> Color("#a231ff")
                4 -> Color("#cc7a00")
                5 -> Color("#ee5700")
                else -> NamedColor.black
            }

            if (isNew) {
                after {
                    content = string("\"N\"")
                    display = Display.flex
                    position = Position.absolute
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    color = NamedColor.white
                    backgroundColor = Color("#f5352e")
                    padding = Padding(0.px, 0.2.em)
                    top = 0.px
                    right = 0.px
                    height = 100.pct
                    fontFamily = string("SourceHanSansCN-Bold")
                    borderRadius = 0.2.em
                }
            }
        }
    }
}
