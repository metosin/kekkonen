# Kekkonen [![Build Status](https://travis-ci.org/metosin/kekkonen.svg?branch=master)](https://travis-ci.org/metosin/kekkonen) [![Dependencies Status](http://jarkeeper.com/metosin/kekkonen/status.svg)](http://jarkeeper.com/metosin/kekkonen)

A library for creating and consuming remote APIs for Clojure(Script). http://kekkonen.io/

## Latest version

[![Clojars Project](http://clojars.org/metosin/kekkonen/latest-version.svg)](http://clojars.org/metosin/kekkonen)

Quickstart: `lein new kekkonen kakkonen --snapshot`

# Mission statement

We are building complex UIs and need great remote api libraries to support that. APIs should be easy to
create, compose and consume. They should be interaction- & domain-driven, not spec-driven (like the REST).
Security should be inbuilt. Clients should be able to browze the apis, validate inputs and business rules
both on the server & on the client. Data should flow both ways. State and dependencies should be managed
elegantly. The library should be named after a Finnish president.

# Idea

- Simple **library** to create and consume apis
- Expose simple Clojure **functions** as **message handlers**
- Manage handlers in **virtual namespaces**
- Invoke or validate input to handlers via a **dispatcher**
- **Schema** to describe messages and do coercion
- Data-driven, no macros, **no magic**
- Declarative dependencies, **security** and state management
- Explicit **extensions** via protocols, options and **meta-data**
- **Transports** abstracted away
  - http via ring, websockets or use the queues, Luke.
- Support different style of **apis**: messages, commands & queries, http
- **Clients** as first-class citizens
  - Ability to **validate** requests against handlers
  - Remote **api documentation** as clojure/json data
  - Public http api documentation via **Swagger**

More on the [Wiki](https://github.com/metosin/kekkonen/wiki/Basics).

Examples projects under [`/examples`](https://github.com/metosin/kekkonen/tree/master/examples).

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

* `kekkonen/all-handlers` list all handlers in the given namespace.
* `kekkonen/available-handlers` all handlers that are available in the given namespace
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

# Thinking aloud

## Why not just use multimethods for dispatch?

Clojure multimethods introduce mutable implicit state. With multimethods, by requiring a namespace `x` you
could get an extra methods for a multimethod as a [side-effect](https://github.com/clojure/clojure/blob/bc186508ab98514780efbbddb002bf6fd2938aee/src/jvm/clojure/lang/MultiFn.java#L58-L68).
For internal functionality (like in the cljs frontends), it's totally awesome and polymorfic.

For remoting, things should be explicit and secure. With Kekkonen, handler registration is explicit and security
works like the UNIX directory structure: by not having access to namespace `:api.admin`, you can't have access
to any anything (sub-namespaces or handler) under that, regardless of their access policies.

## HTTP is awesome, why hide it?

Yes, it is awesome, and is used as a transport. But do you really want to handcraft you domain into `POST`s, `PUT`s
and `PATCH`es do reverse-engineer back in the client? Is it easy to consume APIs that return status codes 
[451](https://github.com/metosin/ring-http-response/blob/fe13051fd89ce073b04b855dcff18a0ce8d07190/dev/user.clj#L57)
or the [226](https://github.com/metosin/ring-http-response/blob/fe13051fd89ce073b04b855dcff18a0ce8d07190/dev/user.clj#L19)?

Kekkonen tries to keep things simple. By abstracting the HTTP we can use plain clojure, websockets or queues without
change in the interaction semantics.

## Is this an actor lib?

No.

# Special thanks

- [Schema](https://github.com/Prismatic/schema) for everything
- [Plumbing](https://github.com/Prismatic/plumbing) for the `fnk`y syntax
- [Fnhouse](https://github.com/Prismatic/fnhouse) for inspiration
- [Ring-swagger](https://github.com/metosin/ring-swagger) for the Schema2Swagger -bindings
- [Ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format) for all the http-transports
- [Compojure-api](https://github.com/metosin/compojure-api) for some middleware goodies

# TODO

- [ ] ClojureScript client
- [ ] JavaScript client
- [ ] re-kekkonen, a Reagent template
- [ ] Emitting Events from commands & Websockets, towards Event Sourcing?

## License

Copyright © 2015 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
