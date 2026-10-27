package top.moles

import emotion.react.Global
import emotion.react.css
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import js.globals.globalThis
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import top.moles.ui.Antd
import top.moles.ui.Card
import top.moles.ui.PageJumper
import top.moles.ui.SelectOption
import top.moles.ui.ark.*
import web.cssom.*
import web.window.WindowTarget
import kotlin.text.Typography.nbsp


val scope = CoroutineScope(Dispatchers.Default)
val ktorClient = HttpClient(Js)
val json = Json {
    ignoreUnknownKeys = true
}





data class PageState(
    var uid: String?,
    var page: UInt,
) {
    companion object {
        val firstPage = 0u
    }
}
val App = FC<Props> {
    var displayData by useState<GachaPage?>(null)
    var pageState by useState(PageState(null, 0u))
    var userList by useState<List<User>>(emptyList())
    fun jumpTo(newPage: UInt) {
        if (newPage == pageState.page) return
        pageState = pageState.copy(page = newPage)
    }

    useEffect(false) {
        val resp = ktorClient.get("/api/ark/users")
        val content = resp.bodyAsText()
        val data = json.decodeFromString<List<User>>(content)
        userList = data
    }
    useEffect(pageState) {
        val resp = ktorClient.get("/api/ark/gachas") {
            parameter("page", pageState.page)
            pageState.uid?.let {
                parameter("uid", it)
            }
        }
        val content = resp.bodyAsText()
        val data = json.decodeFromString<GachaResponse>(content)
        displayData = data.data
    }
    Card {
        children = div.create {
            a {
                + "搜索:"
                css {
                    color = Color("#158fc5")
                }
            }
            Antd.Select {
                css {
                    width = 60.pct
                    marginLeft = 1.em
                }
                showSearch = true
                optionFilterProp = "label"
                options = userList.map {
                    SelectOption(
                        value = it.uid,
                        label = it.nickName,
                        key = it.uid,
                    )
                }.toTypedArray()
                onChange = { value ->
                    pageState = pageState.copy(uid = value, page = PageState.firstPage)
                }
            }
            displayData?.let { displayData ->
                GachaTable {
                    value = displayData.list
                }
                PageJumper {
                    value = displayData.pagination
                    onChange = {
                        pageState = pageState.copy(page = it.newValue)
                    }
                }
            }
            div {
                css {
                    fontSize = (0.75).em
                    color = rgb(86,86,86)
                    margin = Margin(1.em, 1.em)
                }
                + "* 可查询"
                b {
                    + "不止 30日 100条 以内"
                }
                + "有效数据."
            }
            div {
                css {
                    fontSize = (0.75).em
                    color = rgb(86,86,86)
                    margin = Margin(1.em, 1.em)
                }
                + "** 查询结果可能与游戏内实际操作存在延迟."
            }
        }
    }
    Card {
        children = div.create {
            div {
                css {
//                    float = js.reflect.unsafeCast("buttom")
                }
                div {
                    GiftIcon{}
                    a {
                        css {
                            margin = 1.em
                            fontSize = 20.px
                        }
                        +"提交 token 开始晒卡"
                    }
                }
                div {
                    css {
                        margin = 5.px
                    }
                    var inputText = ""
                    form {
                        div {
                            input {
                                css {
                                    textAlign = TextAlign.center

                                    display = Display.block
                                    width = 100.pct
                                    boxSizing = BoxSizing.borderBox

                                    border = Border(1.px, None.none, NamedColor.black)
                                    padding = Padding((0.5).em, (0.2).em)
                                    color = Color("#313131")
                                    fontSize = 1.em
                                    height = 1.em
                                    boxSizing = BoxSizing.contentBox
                                    background = None.none
                                }
                                placeholder = "复制 token 到此"
                                onChange = {
                                    inputText = it.target.value
                                }
                            }
                        }
                    }
                    Button {
                        onClick = {
                            println("submit token $inputText")

                            scope.async {
                                ktorClient.post("/api/ark/register") {
                                    parameter("token", inputText)
                                }

                            }
                        }
                        value = "Submit"
                    }
                }
                div {
                    css {
                        fontSize = (0.75).em
                        color = rgb(86,86,86)
                        margin = Margin(1.em, 0.px)
                    }
                    + "* 请首先在"
                    a {
                        href = "https://ak.hypergryph.com/user"
                        target = WindowTarget._blank
                        + "官网"
                    }
                    + "登录, 随后访问"
                    a {
                        href = "https://web-api.hypergryph.com/account/info/hg"
                        + "此处"
                    }
                    + "复制全部内容 (如了解JSON也可仅复制data中的token), 然后在此填入. (请使用同一浏览器)"
                }
                div {
                    var invalidNameList by useState(emptyList<String>())
                    useEffect(false) {
                        val resp = ktorClient.get("/api/ark/users.invalid")
                        resp.bodyAsText().let {
                            invalidNameList = json.decodeFromString(it)
                        }
                    }
                    css {
                        fontSize = (0.75).em
                        color = rgb(86,86,86)
                        margin = Margin(1.em, 0.px)
                    }
                    + "** token失效的博士:"
                    invalidNameList.forEach {
                        span {
                            + "$nbsp$it$nbsp"
                        }
                    }
                }
            }

        }
    }
    Card {
        children = div.create {
            h3 {
                + "项目介绍"
            }
            div {
                a {
                    + "一个纯 kotlin 编写的明日方舟抽卡记录网站"
                }
                br{}
                a {
                    + "mole828/arknights-gacha-recorder"
                    href = "https://github.com/mole828/arknights-gacha-recorder"
                }
                br{}
                a {
                    + "mole828/arknights-gacha-recorder-agent"
                    href = "https://github.com/mole828/arknights-gacha-recorder-agent"
                }
                br{}
                a {
                    + "mole828/arknights-gacha-recorder-webui"
                    href = "https://github.com/mole828/arknights-gacha-recorder-webui"
                }
            }

        }
    }
    globalThis.set("jumpTo", fun(page: UInt, newUid: String?) {
        pageState.uid = newUid
        jumpTo(page)
    })
    globalThis.set("antd", Antd)

}