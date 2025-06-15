(set! *warn-on-reflection* true)
(ns converter.url
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [converter.str :as str]
   [lambdaisland.uri :as uri]
   [lambdaisland.uri.normalize :as urin]
   [spec-tools.core :as st]))

; TODO only accept file:// urls

; it is assumed that the string is already url encoded
(defn string->url
  [_ str]
  (if (string? str)
    (try
      (uri/uri str)
      (catch Exception _ str))
    str))

(defn url->path
  [url]
  (-> url
      str
      java.net.URL.
      io/as-file
      str))

(defn url-gen
  []
  (gen/fmap (comp uri/uri (partial string/join "/") (partial remove empty?) flatten)
            (gen/tuple
             (gen/elements #{"file://localhost"})
             (gen/one-of [(str/drive-letter-gen) (gen/elements #{""})])
             (->> (str/not-blank-string-with-whitespace-gen)
                  (gen/fmap #(urin/percent-encode % :path))
                  (gen/vector)
                  (gen/not-empty)))))

(s/def ::url (st/spec (s/and
                       #(instance? lambdaisland.uri.URI %) ; TODO is this really needed?
                       #(= "file" (:scheme %)))
                      {:type :url
                       :gen #(url-gen)}))

(defn file-ext
  [url]
  (->> url
       :path
       (re-find #"\.([^\.]+)$")
       second))

(s/fdef drive->wsl
  :args (s/cat :url ::url :wsl? boolean?)
  :ret ::url)

(defn drive->wsl
  [url wsl?]
  (if wsl?
    (uri/join url (string/replace (:path url)
                                  #"^/([A-Z]):/"
                                  #(str "/mnt/" (string/lower-case (% 1)) "/")))
    url))