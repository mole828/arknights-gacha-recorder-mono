package top.moles.ui

import emotion.react.css
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.div
import web.cssom.*


val Card = FC<PropsWithChildren> { props ->
    div {
        css {

            minWidth = 20.em
            minHeight = 8.em
            backgroundColor = Color("hsla(0,0%,100%,.8)")
            borderRadius = 1.2.em
            boxShadow = BoxShadow(0.px, 2.px, 10.px, (-5).px, Color("rgb(9 2 4)"))
            padding = 2.em
            boxSizing = BoxSizing.borderBox

            width = 98.pct
            margin = 1.pct
        }
        +props.children
    }
}