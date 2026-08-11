
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
        .text(`${item.totalSize} (${item.totalCount})`)
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

function selectBenchmarkOptions(name, benchmark) {
    const options = Array.from(document.querySelectorAll(`input[name='${name}']`))
    options.forEach(option => {
        const visible = option.dataset.benchmarks.split(" ").includes(benchmark)
        option.hidden = !visible
        document.querySelector(`label[for='${option.id}']`).hidden = !visible
    })

    if (!options.some(option => option.checked && !option.hidden)) {
        options.find(option => !option.hidden).checked = true
    }
}

function normalizeAllocationData(data) {
    Object.values(data).forEach(location => {
        location.locationSize ??= 0
        Object.values(location.instanceIndex).forEach(instance => {
            instance.totalSize ??= 0
            Object.values(instance.sites).forEach(site => {
                site.totalCount ??= 0
                site.totalSize ??= 0
            })
        })
    })
    return data
}

export function setupRenderControls(drawAllocations) {
    const render = () => {
        const benchmark = document.querySelector("input[name='benchmark']:checked").value
        selectBenchmarkOptions("test", benchmark)
        selectBenchmarkOptions("engine", benchmark)

        const testName = document.querySelector("input[name='test']:checked").value
        const engineName = document.querySelector("input[name='engine']:checked").value
        const snapshotDir = document.querySelector("input[name='snapshot']:checked").value
        const benchmarkDir = benchmark === "client" ? `${snapshotDir}/client` : snapshotDir
        const reportPath = `${benchmarkDir}/${testName}[${engineName}].json`;
        d3.json(reportPath).then(result => {
            drawAllocations(normalizeAllocationData(result.data))
            document.getElementById("info").innerText = ""
        }, () => {
            drawAllocations({})
            document.getElementById("info").innerText = `Nothing found for ${reportPath}`
        })
    }
    document.querySelectorAll("input[type='radio']").forEach(elem => {
        elem.onchange = render
    })

    render()
}