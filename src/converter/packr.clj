(ns converter.packr
  (:require [converter.cli])
  (:import [com.badlogicgames.packr Packr]))

(defn -main
 [& args]
 (Packr/main (into-array args)))