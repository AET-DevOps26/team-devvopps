import { useEffect, useState } from "react";

function App() {
  const [message, setMessage] = useState<string>("");

  useEffect(() => {
    fetch("http://localhost:8080/api/roadmaps/test")
      .then((res: Response) => res.text())
      .then((data: string) => setMessage(data))
      .catch((err: unknown) => {
        console.error("Fetch error:", err);
        setMessage("Error loading data");
      });
  }, []);

  return (
    <div style={{ padding: "40px" }}>
      <h1>TUMgoal Roadmap</h1>
      <p>{message}</p>
    </div>
  );
}

export default App
