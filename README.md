# Citizen Knowledge Graph

This is the prototype for demoing the idea of a "Citizen Knowledge Graph" app and ecosystem. [Vanessa](https://espinosaureta.com/), [@wbglaeser](https://github.com/wbglaeser) and [@benjaminaaron](https://github.com/benjaminaaron) applied with this idea to the 15th round of the [Prototype Fund](https://prototypefund.de).

Parts of it are implemented and others are only mocked for the purpose of the demo. There is also a paragraph-by-paragraph animation on some pages of the app to go well together with the narration in **[this video](https://youtube.com/playlist?list=PLyt46q60EbD9-xm2_0MjYisG2OcVBqhjI)**.

## Setup

```shell
# cd to the root of this repository
./gradlew build
cd demo
npm install
cd ../app
npm install
```

## How to run

Start the Java Spring Boot backend from the root directory:

```sh
./gradlew bootRun
```

Serve the demo files from the `demo` directory:

```sh
python3 -m http.server
```

Start the electron app from the `app` directory:

```sh
npm start
```

If you want to empty your triple store, delete the `jena-tdb` folder.
