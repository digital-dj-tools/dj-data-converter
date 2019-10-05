(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   #?(:clj [taoensso.tufte :as tufte :refer (defnp p profile)]
      :cljs [taoensso.tufte :as tufte :refer-macros (defnp p profile)])))

(defprotocol Converter
  (input-spec [this config])
  (library-spec [this])
  (output-spec [this config]))

(def traktor->rekordbox
  (reify
    Converter
    (input-spec
      [this config]
      (t/nml-spec config))
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this config]
      (r/dj-playlists-spec config))))

(def rekordbox->traktor
  (reify
    Converter
    (input-spec
      [this config]
      (r/dj-playlists-spec config))
    (library-spec
      [this]
      r/library-spec)
    (output-spec
      [this config]
      (t/nml-spec config))))

(defprotocol Edition
  (converter [this config]))

(def basic-edition
  (reify
    Edition
    (converter [this {:keys [input]}]
      (cond
        ; TODO either throw exception if input is anything else
        ; or guarantee it isn't by spec conform etc
        (= input :traktor) traktor->rekordbox
        (= input :rekordbox) rekordbox->traktor))))

(s/fdef convert
  :args (s/cat
         :converter #{traktor->rekordbox}
         :config (spec/such-that-spec config/config-spec #(= :traktor (:input %)))
         :xml (spec/value-encoded-spec (t/nml-spec {}) spec/string-transformer))
  :ret (spec/value-encoded-spec (r/dj-playlists-spec {}) spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [converter config xml]
  (let [input-spec (input-spec converter config)
        library-spec (library-spec converter)
        output-spec (output-spec converter config)]
    (as-> xml $
      (p ::decode-1 (spec/decode! input-spec $ spec/string-transformer))
      (p ::decode-2 (spec/decode! library-spec $ spec/xml-transformer))
      (p ::encode (st/encode output-spec $ spec/xml-transformer)))))