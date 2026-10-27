package top.moles.ui.ark

import emotion.css.css
import emotion.react.css
import react.FC
import react.Props
import react.PropsWithValue
import top.moles.Gacha
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import web.cssom.*

external interface GachaTableProps : PropsWithValue<List<Gacha>>

val GachaTable = FC<GachaTableProps> {
    val gachaList = it.value
    table {
        css {
            width = 100.pct
            borderCollapse = BorderCollapse.separate
            textIndent = Globals.initial
            borderSpacing = 2.px

            " td" {
                textAlign = TextAlign.center
                wordBreak = WordBreak.breakWord
                display = Display.tableCell
                verticalAlign = Globals.inherit
                padding = 10.px
            }
        }

        thead {
            tr {
                td { +"昵称" }
                td { +"时间" }
                td { +"寻访卡池" }
                td { +"获得干员" }
            }
            css {
                fontSize = 0.8.em
                color = Color("#158fc5")
                fontWeight = FontWeight.bolder
            }
        }

        tbody {
            css {
                fontSize = 0.75.em
            }
            for (gacha in gachaList) {
                GachaRow {
                    this.value = gacha
                }
            }
        }
    }
}