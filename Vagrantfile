Vagrant.configure(2) do |config|
    config.vm.box = "ubuntu/trusty64"

  config.vm.provider "virtualbox" do |v|
    v.memory = 1024

    # Enable the VM's virtual USB controller & enable the virtual USB 2.0 controller
    v.customize ["modifyvm", :id, "--usb", "on", "--usbehci", "on"]
  end

  config.vm.network "forwarded_port", guest: 8080, host: 8080
  
  config.vm.provision "shell", privileged: false, inline: <<-SHELL
    sudo apt-get update
    sudo apt-get install -y git

    # Node.js
    curl -sL https://deb.nodesource.com/setup_4.x | sudo bash -
    sudo apt-get install -y nodejs
    sudo apt-get install -y build-essential

    # --no-bin-links workaround for use on top of windows FS
    #npm install --no-bin-links
    npm install http-server
    sudo npm install -g coffeescript

SHELL


end

