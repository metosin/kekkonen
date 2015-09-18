# Kekkonen [![Build Status](https://travis-ci.org/metosin/kekkonen.svg?branch=master)](https://travis-ci.org/metosin/kekkonen)

A library for creating and consuming remote APIs for Clojure(Script). http://kekkonen.io/

Status: **Alpha**, `0.1.0` will be released soon.

# Mission statement

We are building complex UIs and need great remote api libraries to support that. APIs should be easy to
create, compose and consume. They should be interaction- & domain-driven, not spec-driven (like the REST).
Security should be inbuilt. One should be able to validate business rules both on the client and on the server
without duplicating the code and logic. Data should flow both ways. State and dependencies should be managed
elegantly. The library should be named after a Finnish president.

# Idea

- Simple **library** to create and consume apis
- Expose simple Clojure **functions** as message handlers
- Manage handlers in virtual **namespaces** to enable refactoring
- **Schema** for data descriptions and coercion
- Data-driven, no macros, **no magic**
- Declarative dependencies and state management
- Explicit **extensions** via protocols, options and **meta-data**
- **Transports** abstracted away
  - http via ring, websockets or use the queues, Luke.
- **Clients** as first-class citizens
  - Ability to **validate** requests against handlers
  - Remote **api documentation** as clojure/json data
  - Public http api documentation via **Swagger**
  
# A Simple example

## Creating an API

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
     :core {:handlers {:api {:pizza #'echo-pizza
                             :example [#'ping #'inc! #'plus]}}
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

## Consuming an API

By default, Kekkonen supports the most common wire-formats (`json`, `edn`, `yaml`, `transit-json` 
and `transit-msgpack`) for all APIs (and errors), selected by the standard content-negotiation process.

Clients can also choose whether they want the handler to actually process the request or just validate the
context against the handler. With http-apis, this is done by setting a `kekkonen.mode` header to either
`validate` or `invoke`. By not emitting the header, `invoke` is used.

Examples with [httpie](https://github.com/jkbrzt/httpie):

Invoking a handler with invalid input:

```bash
➜  http :3000/api/sample/plus
HTTP/1.1 400 Bad Request
Content-Length: 127
Content-Type: application/json; charset=utf-8
Date: Wed, 16 Sep 2015 21:09:41 GMT
Server: http-kit

{
    "error": {
        "x": "missing-required-key",
        "y": "missing-required-key"
    },
    "in": "query-params",
    "type": "kekkonen.ring/request",
    "value": {}
}
```

Invoking a handler with valid input:

```bash
➜  http :3000/api/sample/plus x==1 y==2
HTTP/1.1 200 OK
Content-Length: 12
Content-Type: application/json; charset=utf-8
Date: Wed, 16 Sep 2015 21:13:43 GMT
Server: http-kit

{
    "result": 3
}
```

Validating a handler with valid input (does not run the actual body):

```bash
➜  http :3000/api/sample/plus x==1 y==2 kekkonen.mode:validate
HTTP/1.1 200 OK
Content-Length: 0
Date: Wed, 16 Sep 2015 21:14:39 GMT
Server: http-kit

```

There are also few special endpoints mounted in the `kekkonen` namespace:

* `kekkonen/get-all` list all handlers in the given namespace.
* `kekkonen/get-available` all handlers that are available for the current context (handler rules applied, no body)
* `kekkonen/get-handler` info of a single handler.

Example call to the get-handler:

```bash
➜  http :3000/kekkonen/get-handler action==api.pizza/echo-pizza
HTTP/1.1 200 OK
Content-Length: 436
Content-Type: application/json; charset=utf-8
Date: Wed, 16 Sep 2015 21:25:26 GMT
Server: http-kit

{
    "action": "api.pizza/echo-pizza",
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
    "ns": "api.pizza",
    "output": "Any",
    "source-map": {
        "column": 1,
        "file": "/Users/tommi/projects/metosin/kekkonen/dev-src/example/api.clj",
        "line": 24,
        "name": "echo-pizza",
        "ns": "example.api"
    },
    "type": "command"
}
```

There will be both a Clojure(Script) and JavaScript client library to wotk with the handler metadata.

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
