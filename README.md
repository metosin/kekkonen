# Kekkonen [![Build Status](https://travis-ci.org/metosin/kekkonen.svg?branch=master)](https://travis-ci.org/metosin/kekkonen)

<img src="https://raw.githubusercontent.com/wiki/metosin/kekkonen/kekkonen.png" align="right"/>

A&nbsp;lightweight, data-driven library for creating and consuming remote service with Clojure(Script). Key features:
* not dependent on Ring/HTTP/REST, just your domain functions & data
* enables apis over HTTP, Web Sockets, Message Queues or whatever
* supports multiple api styles: Messaging, CQRS & HTTP
* [Schema](https://github.com/Prismatic/schema) input & output coercion
* live & secure api-docs with [Swagger](http://swagger.io/)
* besides invoking handlers, clients are enable to:
  * securely browse the api namespaces at runtime
  * check & validate single or multiple handlers without side-effects
  * extract public handler meta-data for client-side reasoning
* highly extensible via options, interceptors and meta-handlers
  * ships with sensible defaults

Bubblin' Under:
* all interceptors, fully async
* support for speculative transactions
* client-side bundled reads & writes

<sub>Picture of [UKK](https://en.wikipedia.org/wiki/Urho_Kekkonen) © Pressfoton Etyk 1975 -team, Museovirasto</sub>

See [Live demo](https://kekkonen.herokuapp.com/) & [Wiki](https://github.com/metosin/kekkonen/wiki).

## Latest version

[![Clojars Project](http://clojars.org/metosin/kekkonen/latest-version.svg)](http://clojars.org/metosin/kekkonen)

Quickstart: `lein new kekkonen kakkonen`

## Basic building blocks

### Handler

```clj
{:name ::plus
 :type :handler
 :interceptors []
 :input {:data {:y s/Int
                :x s/Int}}
 :output s/Int
 :handle (fn [{{:keys [x y]} :data}]
           (+ x y))}
```

### Interceptor

```clj
{:name ::require-roles
 :enter (fn [context]
          (let [roles (-> context :user :roles)]
            (if (seq (clojure.set/intersection roles required))
              context)))}
```

## Hello World (local dispatch)

```clj
(require '[kekkonen.core :as k])

(def dispatcher
  (k/dispatcher
    {:handlers
     {:api (k/handler {:name :hello
                       :handle (constantly "hello world"))}}}))

(k/invoke dispatcher :api/hello)
; => "hello world"
```

## Hello World (ring-based Query API)

```clj
(require '[kekkonen.cqrs :refer :all])
(require '[org.httpkit.server :as server])

(defn ^:query hello
  {:input {:data {:name String}}}
  [ctx]
  (success (str "Hello, " (-> ctx :data :name))))

(server/run-server
  (cqrs-api {:core {:handlers #'hello}}})
  {:port 4000})
```

you can invoke the hello api with http://localhost:4000/hello?name=World

## CQRS API with Swagger Docs

```clj
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

(defnk ^:command inc! [counter]
  (success {:result (swap! counter inc)}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:swagger {:ui "/api-docs"
               :spec "/swagger.json"
               :data {:info {:title "Kekkonen example"}}}
     :core {:handlers {:api {:pizza #'echo-pizza
                             :example [#'ping #'inc! #'plus]}}
            :context {:counter (atom 0)}}}))

;;
;; Start it
;;

(comment
  (server/run-server #'app {:port 3000}))
```

Start the server and browse to http://localhost:3000/api-docs and you should see the following:

![swagger-example](https://raw.githubusercontent.com/wiki/metosin/kekkonen/swagger-example.png)

More examples at [`/examples`](https://github.com/metosin/kekkonen/tree/master/examples) and
info in the [Wiki](https://github.com/metosin/kekkonen/wiki/Basics).

# Roadmap

Mostly written as [issues](https://github.com/metosin/kekkonen/issues). Biggest things:

* Create namespaces with handlers from external sources (db, file, [actors](https://github.com/puniverse/pulsar))
* Adapter for Websockets
* (ClojureScript) api-docs beyond Swagger
* Support for Om Next Remotes
* Clojure(Script) client & project template (re-kekkonen)
* Opinionated CQRS reference implementation, with eventing
* Graph-based dependency management
* Handler mutations & hot-swapping
* Go Async

# Presentations

* ClojureD 2016: http://www.slideshare.net/metosin/wieldy-remote-apis-with-kekkonen-clojured-2016
* CLojuTRE 2015: http://www.slideshare.net/metosin/clojutre2015-kekkonen-making-your-clojure-web-apis-more-awesome

# Thinking aloud

## Why not just use multimethods for dispatch?

Clojure multimethods introduce mutable implicit state. With multimethods, by requiring a namespace `x` you
could get an extra methods for a multimethod as a [side-effect](https://github.com/clojure/clojure/blob/bc186508ab98514780efbbddb002bf6fd2938aee/src/jvm/clojure/lang/MultiFn.java#L58-L68).
For internal functionality (like in the cljs frontends), it's totally awesome and polymorphic.

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

## Looks similar to Fnhouse?

Yes, we have reused many great ideas from fnhouse, see [Special Thanks](#special-thanks). Initial version of Kekkonen
was supposed to be built on top of fnhouse but the we realized that most of the fnhouse internals would have had to be
overridden due to difference in opinions.

## Is this an actor lib?

No. But we might integrate into [Pulsar](https://github.com/puniverse/pulsar).

# Special thanks

- [Schema](https://github.com/Prismatic/schema) for everything
- [Plumbing](https://github.com/Prismatic/plumbing) for the `fnk`y syntax
- [Fnhouse](https://github.com/Prismatic/fnhouse) for inspiration and reused ideas
- [Ring-swagger](https://github.com/metosin/ring-swagger) for the Schema2Swagger -bindings
- [Muuntaja](https://github.com/metosin/muuntaja) for all the data formats
- [Compojure-api](https://github.com/metosin/compojure-api) for some middleware goodies

## License

Copyright © 2015-2018 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License 2.0.
