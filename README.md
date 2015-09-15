# Kekkonen

A library for creating and consuming remote APIs for Clojure(Script). http://kekkonen.io/

Status: **Alpha**, `0.1.0` will be released soon.

# Mission statement

We are building complex UIs and need great remote api libraries to support that. APIs should be easy to
create, compose and consume. They should be interaction- & domain-driven, not spec-driven (like the REST).
Security should be inbuilt. One should be able to run the (context-aware) business rules both on the client
and on the server without duplicating the code and logic. Both pull and push models should be supported.
State and dependencies should be managed elegantly. The library should be named after a Finnish president.

# Idea

- Simple **library** to create and consume apis
- Expose simple Clojure **functions** as message handlers
- Manage handlers in virtual **namespaces** to enable refactorings
- **Schema** for data descriptions and coercion
- Data-driven, no macros, **no magic**
- Declarative dependencies and state management
- Explicit **extensions** via protocols, options and **meta-data**
- **Transports** abstracted away
  - http via ring, websockets or use the queues, Luke.
- **Clients** as first-class citizens
  - Remote **api documentation** as clojure/json data
    - client helpers for both ClojureScript & Javascript
  - Public http api documentation via **Swagger**
  
# A Simple example

## The Server

```clojure
(ns example.api
  (:require [org.httpkit.server :as server]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :S :M :L)
   :origin {:country (s/enum :FI :PO)}})

;;
;; Handlers
;;

(defnk ^:query ping []
  (success {:ping "pong"}))

(defnk ^:command echo-pizza
  "Echoes a pizza"
  {:responses {:default {:schema Pizza}}}
  [data :- Pizza]
  (success data))

(defnk ^:query plus
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(defnk ^:command inc!
  [[:components counter]]
  (success {:result (swap! counter inc)}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen example"}}
     :core {:handlers {:api {:example [#'echo-pizza #'plus #'ping]
                             :state #'inc!}}
            :context {:components {:counter (atom 0)}}}}))

;;
;; Start it
;;

(defn start []
  (server/run-server #'app {:port 3000}))

(comment
  (start))
```

Start the server and browse to http://localhost:3000 and you should see the following:

![swagger-example](https://raw.githubusercontent.com/wiki/metosin/kekkonen/swagger-example.png)

## The Client

The APIs prodive few extra endpoints for the clients to use, these are found at `kekkonen` namespace. There will
be both a Clojure(Script) and JavaScript client libraries easy operation of these.

* `get-all` all (exposed) handlers in the api
* `get-available` all handlers that can be called by the user (runs validations without calling the body)
* `get-validated` all handlers that can be called by the user with the given context.
* `get-handler` info a single handler.
* `validate` runs all validations of the handler with the given context without calling the actual body.

Sample result of endpoint `/kekkonen/get-handler?action=api.example/echo-pizza` as JSON:

```json
{
    "action": "api.example/echo-pizza",
    "input": {
        "Keyword": "Any",
        "data": {
            "#schema.core.OptionalKey{:k :description}": "java.lang.String",
            "name": "java.lang.String",
            "origin": {
                "country": "(enum :PO :FI)"
            },
            "size": "(enum :L :M :S)"
        }
    },
    "name": "echo-pizza",
    "ns": "api.example",
    "output": "Any",
    "source-map": {
        "column": 1,
        "file": "/Users/tommi/projects/metosin/kekkonen/dev-src/example/simple.clj",
        "line": 24,
        "name": "echo-pizza",
        "ns": "example.api"
    },
    "type": "command"
}
```

# Special thanks

- [Schema](https://github.com/Prismatic/schema) for everything
- [Plumbing](https://github.com/Prismatic/plumbing) for the `fnk`y syntax
- [Fnhouse](https://github.com/Prismatic/fnhouse) for inspiration
- [Ring-swagger](https://github.com/metosin/ring-swagger) for the Schema2Swagger -bindings
- [Ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format) for all the http-transports
- [Compojure-api](https://github.com/metosin/compojure-api) for some middleware goodies

# TODO

- [ ] Finalize things
- [ ] Wiki tutorial, describing internals
- [ ] ClojureScript client
- [ ] JavaScript client
- [ ] re-kekkonen, a Reagent template
- [ ] Emitting Events / Websockets, towards Event Sourcing?
- [ ] Ring -> Async
- [ ] Web-schemas

## License

Copyright © 2015 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
