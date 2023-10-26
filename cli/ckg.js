const fs = require("fs")
const path = require("path")

async function importDependencies() {
    const [{ default: rdfExt }, { default: ParserN3 }, { default: SHACLValidator }] = await Promise.all([
        import("rdf-ext"),
        import("@rdfjs/parser-n3"),
        import("rdf-validate-shacl")
    ])
    return { factory: rdfExt, ParserN3, SHACLValidator }
}

const profilesDir = path.join(__dirname, "profiles")
const queriesDir = path.join(__dirname, "queries")

async function loadDataset(filePath) {
    const { factory, ParserN3 } = await importDependencies()
    const stream = fs.createReadStream(filePath)
    const parser = new ParserN3()
    return factory.dataset().import(parser.import(stream))
}

async function runQueryOnProfile(queryName, profileName) {
    const { factory, SHACLValidator } = await importDependencies()
    const shapes = await loadDataset(path.join(queriesDir, `${queryName}.ttl`))
    const data = await loadDataset(path.join(profilesDir, `${profileName}.ttl`))

    const validator = new SHACLValidator(shapes, { factory })
    const report = await validator.validate(data)

    // get report details: https://github.com/zazuko/rdf-validate-shacl#usage

    console.log("--> " + profileName + " is" + (report.conforms ? " " : " not ") + "eligible for " + queryName)
}

const commands = {
    "run-query-on-profile": runQueryOnProfile,
}

const [, , command, ...args] = process.argv

if (commands[command]) {
    commands[command](...args)
} else {
    console.error("Unknown command:", command)
}
