<html>
<head>
    <title>Welcome Page</title>
</head>
<body>
<h2>Hello World!</h2>
<h2><a href="javascript: return false;" onclick="redirect('screen-sharing')">ScreenSharing</a></h2>
<script>
    function redirect(link) {
        window.location.href = "https://" + location.hostname + "/" + link;
    }
</script>
</body>
</html>
