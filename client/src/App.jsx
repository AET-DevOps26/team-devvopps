import { useEffect, useState } from "react";

function App() {

  const [message, setMessage] = useState("");

  useEffect(() => {

    fetch("http://localhost:8080/api/roadmaps/test")
      .then(res => res.text())
      .then(data => setMessage(data));

  }, []);

  return (
    <div style={{ padding: "40px" }}>
      <h1>TUM Roadmap App</h1>
      <p>{message}</p>
    </div>
  );
}

export default App;