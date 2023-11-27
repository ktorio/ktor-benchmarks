
const formatSize = (size) => Math.round(size / 1024 / 1024 * 1000) / 1000 + "Mb"

export function displaySite(sites, item) {
    const site = sites.append("div")
        .attr("class", "site")
        .attr("data-expand", "false")

    const stackTrace = item.stackTrace.split(", ")

    const siteElem = site.append("div")
        .attr("class", "header")
    siteElem.append("div").attr("class", "chevron")
    siteElem.append("span")
        .style("font-weight", "bold")
        .text(`${formatSize(item.totalSize)} (${item.totalCount})`)
    siteElem.append("span")
        .text(stackTrace[1].split(" ")[0])
    siteElem.on("click", () => {
        site.attr("data-expand", site.attr("data-expand") === "false")
    })

    const stack = site.append("ul")
        .attr("class", "stacktrace")
        .style("list-style-type", "none")

    stackTrace.forEach((stackItem) => {
        const li = stack.append("li")
        const [file, fun] = stackItem.split(" ")
        li.append("span").attr("class", "file").text(file)
        li.append("span").attr("class", "fun").text(fun)
    })
}