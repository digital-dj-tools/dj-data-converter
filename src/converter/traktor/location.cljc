#?(:clj (set! *warn-on-reflection* true))
(ns converter.traktor.location
  (:require
   [cemerick.url :refer [url url-encode url-decode]]
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.string :as string]
   [clojure.zip :as zip]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.traktor.nml :as nml]
   [converter.url :as url]
   [spec-tools.data-spec :as std]))

(def location
  {:tag (s/spec #{:LOCATION})
   :attrs {:DIR ::nml/nml-dir
           :FILE string?
           (std/opt :VOLUME) (std/or {:drive-letter ::str/drive-letter
                                      :named string?})
           (std/opt :VOLUMEID) string?}})

(def location-spec
  (spec/such-that-spec
   (std/spec {:name ::location
              :spec location})
   #(or (and (-> % :attrs :VOLUME) (-> % :attrs :VOLUMEID))
        (and (not (-> % :attrs :VOLUME)) (not (-> % :attrs :VOLUMEID))))))

(s/fdef url->location
  :args (s/cat :url ::url/url)
  :ret location-spec
  :fn (fn equiv-location? [{{conformed-url :url} :args conformed-location :ret}]
        (let [url (s/unform ::url/url conformed-url)
              location-z (zip/xml-zip (s/unform location-spec conformed-location))]
          (re-matches nml/nml-dir-regex (zx/attr location-z :DIR)))))

(defn url->location
  [{:keys [:path]}]
  (let [paths (rest (string/split path #"/"))
        dirs (if (str/drive-letter? (first paths)) (rest (drop-last paths)) (drop-last paths))
        file (last paths)
        volume (if (str/drive-letter? (first paths)) (first paths))]
    {:tag :LOCATION
     :attrs (cond-> {:DIR (nml/nml-dir (map url-decode dirs))
                     :FILE (url-decode file)}
              volume (assoc :VOLUME volume))}))

(defn location-z-file-is-not-blank?
  [location-z]
  (not (string/blank? (zx/attr location-z :FILE))))

; TODO would rather use data.zip.xml api all the way down, 
; but spec/such-that-spec can't currently be wrapped around spec/xml-zip-spec
(defn location-file-is-not-blank?
  [location]
  (location-z-file-is-not-blank? (zip/xml-zip location)))

(s/fdef location->url
  :args (s/cat :location-z (spec/xml-zip-spec (spec/such-that-spec location-spec location-file-is-not-blank? 100)))
  :ret ::url/url)

(defn location->url
  [location-z]
  (let [dir (zx/attr location-z :DIR)
        file (zx/attr location-z :FILE)
        volume (zx/attr location-z :VOLUME)]
    (apply url (as-> [] $
                 (conj $ "file://localhost")
                 (conj $ (if (str/drive-letter? volume) (str "/" volume) ""))
                 (reduce conj $ (map url-encode (string/split dir nml/nml-path-sep-regex)))
                 (conj $ (url-encode file))))))