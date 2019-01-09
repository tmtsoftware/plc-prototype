if [ $# -eq 0 ]; then
  echo "Usage: initialize-config.sh <config service IP> "
  exit 0
fi


curl -X POST --data-binary @plcHcdConfig.conf http://$1:5000/config/org/tmt/plcHcdConfig.conf