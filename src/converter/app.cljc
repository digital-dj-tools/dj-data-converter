(ns converter.app
  (:require
   [cemerick.url :refer [url-decode]]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.offset :as o]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [converter.url :as url]
   [converter.xml :as xml]
   [mp3-parser.app :as mp3]
   [spec-tools.core :as st]))

(defprotocol TraktorRekordboxConverter
  (input-spec [this])
  (input-string-transformer [this])
  (input-xml-transformer [this])
  (library-spec [this])
  (output-spec [this progress])
  (output-xml-transformer [this]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (input-spec
      [this]
      (t/nml-spec))
    (input-string-transformer
      [this]
      t/string-transformer)
    (input-xml-transformer
      [this]
      t/xml-transformer)
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this progress]
      (r/dj-playlists-spec progress))
    (output-xml-transformer
     [this]
     r/xml-transformer)))

(defn correct
  [options item]
  (let [url (-> item ::u/location (url/drive->wsl (:wsl options)))]
    (try
      (o/correct item (-> url url/url->path mp3/parse))
    ; TODO don't print, conj report with any error from mp3-parser, e.g. file not found
      #?(:clj (catch Throwable t (do (println (ex-message t)) item))
         :cljs (catch :default e (do (println (ex-message e)) item))))))

(s/fdef convert
  :args (s/cat :config #{{:converter traktor->rekordbox}}
               :options map?
               :xml (spec/value-encoded-spec (t/nml-spec) t/string-transformer))
  :ret (spec/value-encoded-spec r/dj-playlists-spec r/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [config options xml]
  (let [converter (:converter config)
        input-spec (input-spec converter)
        library-spec (library-spec converter)
        output-spec (output-spec converter (:progress config))]
    (as-> xml $
      (spec/decode! input-spec $ (input-string-transformer converter))
      (spec/decode! library-spec $ (input-xml-transformer converter))
      ; TODO only correct mp3 files?
      (update $ ::u/collection #(map (partial correct options) %))
      (st/encode output-spec $ (output-xml-transformer converter)))))
