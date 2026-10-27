package top.moles.ui.ark

import emotion.react.css
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.internal.JSJoda.ZoneId
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.PropsWithChildren
import react.PropsWithClassName
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import top.moles.Gacha
import web.cssom.*
import web.cssom.atrule.width

external interface GachaProps : PropsWithClassName {
    var value: Gacha
}

val gachaTd = FC<PropsWithChildren> {
    td {
        +children
    }
}

val GachaRow = FC<GachaProps> { props ->
    val gacha = props.value

    fun PropsWithClassName.contextCss(width: Length) {
        css {
            textAlign = TextAlign.center
            wordBreak = WordBreak.breakWord
            display = Display.tableCell
            verticalAlign = Globals.inherit
            padding = 10.px

            this.width = width
        }
    }


    tr {
        css {
            not(":last-child") {
                " td" {
                    borderBottom = Border(
                        width = 1.px,
                        style = LineStyle.solid,
                        color = Color("#e0e0e0")
                    )
                }
            }
        }
        td {
            contextCss(30.pct)
            +gacha.nickName
        }
        td {
            contextCss(25.pct)
            val time = Instant.fromEpochSeconds(gacha.ts)
            val timeZone = TimeZone.of(ZoneId.systemDefault().id())
            +"${time.toLocalDateTime(timeZone)}"
        }
        td {
            contextCss(25.pct)
            +gacha.pool
        }
        td {
            contextCss(20.pct)
            ol {
                css {
                    margin = 0.em
                    padding = 0.em
                    listStyleType = None.none
                }
                gacha.chars.map { char ->
                    CharRow {
                        name = char.name
                        rarity = char.rarity
                        isNew = char.isNew
                    }
                }
            }
        }
    }
}
