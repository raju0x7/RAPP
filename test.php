<?php   
   echo "Argument: ".$argv[1]."\n";   

   // check if argument is a valid URL
   if(filter_var($argv[1], FILTER_VALIDATE_URL)) {      
      // parse URL
      $r = parse_url($argv[1]);
      print_r($r);      

      // check if host ends with google.com
      if(preg_match('/google\.com$/', $r['host'])) {         
         // get page from URL
         exec('curl -v -s "'.$r['host'].'"', $a);
         print_r($a);
      } else {
         echo "Error: Host not allowed";
      }
   } else {
      echo "Error: Invalid URL";
   }
?>